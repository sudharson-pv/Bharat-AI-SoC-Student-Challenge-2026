package com.learnmate.ai.ai

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.regex.Pattern

/**
 * Production-hardened, offline TikToken BPE Tokenizer Adapter for Llama 3.2 1B Instruct.
 *
 * Invariants:
 * 1. Strict vocabulary validation: exactly 128,000 unique ranks (0..127999), 128,000 unique byte
 *    sequences, all 256 single-byte fallback entries, SHA-256 asset verification.
 * 2. Complete special-token mappings: all 256 special tokens (128000..128255) verified.
 * 3. Stateful UTF-8 streaming decoding: prevents replacement characters for multi-byte tokens.
 * 4. Long-input safeguards: 400,000-char outer chunks, 25,000-char runs, surrogate-pair safety.
 * 5. Zero-GC map rebuilds: O(1) indexed array lookup using pre-allocated Array<ByteArray?>(128256).
 * 6. Neutralizes untrusted prompt injection by defaulting allowSpecial=false.
 */
class Llama3TikTokenAdapter private constructor(
    inputStream: InputStream,
    expectedSha256: String = ActiveModelManifest.EXPECTED_TOKENIZER_SHA256,
    enforceSha256: Boolean = true
) {

    class ByteArrayKey(
        val bytes: ByteArray,
        val offset: Int = 0,
        val length: Int = bytes.size
    ) {
        private val hash: Int

        init {
            var h = 1
            for (i in offset until (offset + length)) {
                h = 31 * h + bytes[i]
            }
            hash = h
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ByteArrayKey) return false
            if (this.length != other.length) return false
            for (i in 0 until length) {
                if (this.bytes[this.offset + i] != other.bytes[other.offset + i]) return false
            }
            return true
        }

        override fun hashCode(): Int = hash
    }

    private val ranks = HashMap<ByteArrayKey, Int>(135_000)
    private val tokenToBytes = arrayOfNulls<ByteArray>(ActiveModelManifest.TOTAL_VOCAB_SIZE)
    private val regexPattern: Pattern

    companion object {
        const val BASE_VOCAB_SIZE = ActiveModelManifest.BASE_VOCAB_SIZE // 128,000
        const val SPECIAL_TOKENS_COUNT = ActiveModelManifest.SPECIAL_TOKENS_COUNT // 256
        const val TOTAL_VOCAB_SIZE = ActiveModelManifest.TOTAL_VOCAB_SIZE // 128,256

        const val BOS_ID = 128_000
        const val EOS_ID = 128_001
        const val RESERVED_0 = 128_002
        const val RESERVED_1 = 128_003
        const val RESERVED_2 = 128_004
        const val RESERVED_3 = 128_005
        const val START_HEADER_ID = 128_006
        const val END_HEADER_ID = 128_007
        const val RESERVED_4 = 128_008
        const val EOT_ID = 128_009

        const val TIKTOKEN_MAX_ENCODE_CHARS = 400_000
        const val MAX_NO_WHITESPACES_CHARS = 25_000
        const val ABSOLUTE_MAX_INPUT_CHARS = 1_000_000

        private const val PATTERN_STR =
            """(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\r\n\p{L}\p{N}]?\p{L}+|\p{N}{1,3}| ?[^\s\p{L}\p{N}]+[\r\n]*|\s*[\r\n]+|\s+(?!\S)|\s+"""

        @Volatile
        private var instance: Llama3TikTokenAdapter? = null

        fun getInstance(
            modelFile: File,
            expectedSha256: String = ActiveModelManifest.EXPECTED_TOKENIZER_SHA256,
            enforceSha256: Boolean = true
        ): Llama3TikTokenAdapter {
            return instance ?: synchronized(this) {
                instance ?: Llama3TikTokenAdapter(
                    inputStream = FileInputStream(modelFile),
                    expectedSha256 = expectedSha256,
                    enforceSha256 = enforceSha256
                ).also { instance = it }
            }
        }

        fun createForTest(
            inputStream: InputStream,
            expectedSha256: String = ActiveModelManifest.EXPECTED_TOKENIZER_SHA256,
            enforceSha256: Boolean = true
        ): Llama3TikTokenAdapter {
            return Llama3TikTokenAdapter(inputStream, expectedSha256, enforceSha256)
        }
    }

    init {
        // Enforce Unicode character class matching for whitespace and multilingual scripts
        regexPattern = Pattern.compile(PATTERN_STR, Pattern.UNICODE_CHARACTER_CLASS)

        val md = MessageDigest.getInstance("SHA-256")
        val dis = DigestInputStream(inputStream, md)

        val seenRanks = BooleanArray(BASE_VOCAB_SIZE)
        val singleByteSeen = BooleanArray(256)
        var lineCount = 0

        BufferedReader(InputStreamReader(dis, StandardCharsets.UTF_8), 65536).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: break
                if (l.isEmpty()) continue
                lineCount++

                val spaceIdx = l.indexOf(' ')
                if (spaceIdx <= 0 || spaceIdx == l.length - 1) {
                    throw IllegalStateException("Malformed tokenizer line structure at entry $lineCount: '$l'")
                }

                val b64Token = l.substring(0, spaceIdx)
                val rankStr = l.substring(spaceIdx + 1).trim()
                val rank = try {
                    rankStr.toInt()
                } catch (e: NumberFormatException) {
                    throw IllegalStateException("Invalid rank integer at entry $lineCount: '$rankStr'", e)
                }

                if (rank !in 0 until BASE_VOCAB_SIZE) {
                    throw IllegalStateException("Rank $rank out of valid range 0..${BASE_VOCAB_SIZE - 1} at entry $lineCount")
                }
                if (seenRanks[rank]) {
                    throw IllegalStateException("Duplicate rank detected: $rank at entry $lineCount")
                }
                seenRanks[rank] = true

                val tokenBytes = try {
                    Base64.getDecoder().decode(b64Token)
                } catch (e: IllegalArgumentException) {
                    throw IllegalStateException("Malformed Base64 token at entry $lineCount: '$b64Token'", e)
                }

                val key = ByteArrayKey(tokenBytes, 0, tokenBytes.size)
                if (ranks.containsKey(key)) {
                    throw IllegalStateException("Duplicate token byte sequence detected at entry $lineCount: rank $rank")
                }

                ranks[key] = rank
                tokenToBytes[rank] = tokenBytes

                if (tokenBytes.size == 1) {
                    val bVal = tokenBytes[0].toInt() and 0xFF
                    singleByteSeen[bVal] = true
                }
            }
        }

        // 1. Strict entry count verification
        if (lineCount != BASE_VOCAB_SIZE) {
            throw IllegalStateException("Expected exactly $BASE_VOCAB_SIZE mergeable ranks, but loaded $lineCount entries")
        }

        // 2. Strict SHA-256 verification
        val digestBytes = md.digest()
        val computedSha256 = digestBytes.joinToString("") { "%02x".format(it) }
        if (enforceSha256 && !computedSha256.equals(expectedSha256, ignoreCase = true)) {
            throw SecurityException(
                "Tokenizer model integrity check failed! Expected SHA-256: $expectedSha256, computed: $computedSha256"
            )
        }

        // 3. Strict single-byte fallback verification (all 256 bytes must be representable)
        for (b in 0..255) {
            if (!singleByteSeen[b]) {
                throw IllegalStateException("Missing single-byte fallback entry for byte 0x${"%02X".format(b)}")
            }
        }

        // 4. Populate all 256 special token decode mappings (128000..128255)
        populateSpecialTokens()
    }

    private fun populateSpecialTokens() {
        val specialNames = arrayOf(
            "<|begin_of_text|>",
            "<|end_of_text|>",
            "<|reserved_special_token_0|>",
            "<|reserved_special_token_1|>",
            "<|reserved_special_token_2|>",
            "<|reserved_special_token_3|>",
            "<|start_header_id|>",
            "<|end_header_id|>",
            "<|reserved_special_token_4|>",
            "<|eot_id|>"
        )

        for (i in specialNames.indices) {
            val id = BASE_VOCAB_SIZE + i
            tokenToBytes[id] = specialNames[i].toByteArray(StandardCharsets.UTF_8)
        }

        // Reserved special tokens 5 through 250 (IDs 128010 to 128255)
        for (i in 5 until (SPECIAL_TOKENS_COUNT - 5)) {
            val id = BASE_VOCAB_SIZE + 5 + i
            tokenToBytes[id] = "<|reserved_special_token_$i|>".toByteArray(StandardCharsets.UTF_8)
        }

        // Verify all 256 special token slots are strictly non-null
        for (id in BASE_VOCAB_SIZE until TOTAL_VOCAB_SIZE) {
            if (tokenToBytes[id] == null) {
                throw IllegalStateException("Unpopulated special token ID: $id")
            }
        }
    }

    /**
     * Encodes untrusted user or retrieved text safely.
     * All control tokens (<|...|>) are strictly encoded as natural text to prevent prompt injection.
     */
    fun encode(text: String?): List<Int> {
        if (text.isNullOrEmpty()) return emptyList()

        if (text.length > ABSOLUTE_MAX_INPUT_CHARS) {
            throw IllegalArgumentException(
                "Input length (${text.length} chars) exceeds absolute safety limit ($ABSOLUTE_MAX_INPUT_CHARS chars)"
            )
        }

        val result = ArrayList<Int>()

        // Meta reference outer chunking: 400,000 characters with surrogate-pair preservation
        var chunkStart = 0
        while (chunkStart < text.length) {
            var chunkEnd = (chunkStart + TIKTOKEN_MAX_ENCODE_CHARS).coerceAtMost(text.length)
            // Adjust boundary to avoid breaking high-low surrogate pairs
            if (chunkEnd < text.length && Character.isHighSurrogate(text[chunkEnd - 1])) {
                chunkEnd--
            }

            val outerChunk = text.substring(chunkStart, chunkEnd)
            encodeChunkWithRunLimit(outerChunk, result)
            chunkStart = chunkEnd
        }

        return result
    }

    /**
     * Splits strings with consecutive runs of whitespace or non-whitespace exceeding 25,000 characters.
     */
    private fun encodeChunkWithRunLimit(s: String, outTokens: MutableList<Int>) {
        if (s.isEmpty()) return

        var currentSliceLen = 0
        var currentSliceIsSpace = Character.isWhitespace(s[0].code)
        var sliceStart = 0

        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val charCount = Character.charCount(cp)
            val isNowSpace = Character.isWhitespace(cp)

            if (currentSliceIsSpace xor isNowSpace) {
                currentSliceLen = 1
                currentSliceIsSpace = isNowSpace
            } else {
                currentSliceLen += charCount
                if (currentSliceLen > MAX_NO_WHITESPACES_CHARS) {
                    val sub = s.substring(sliceStart, i)
                    encodeRegexPieces(sub, outTokens)
                    sliceStart = i
                    currentSliceLen = charCount
                }
            }
            i += charCount
        }

        if (sliceStart < s.length) {
            encodeRegexPieces(s.substring(sliceStart), outTokens)
        }
    }

    private fun encodeRegexPieces(segment: String, outTokens: MutableList<Int>) {
        val matcher = regexPattern.matcher(segment)
        while (matcher.find()) {
            val chunk = matcher.group()
            val piece = chunk.toByteArray(StandardCharsets.UTF_8)
            bpeEncode(piece, outTokens)
        }
    }

    /**
     * High-performance BPE merge using array-based doubly-linked list.
     * Performs zero ArrayList shifts and zero slice memory allocations.
     */
    private fun bpeEncode(pieceBytes: ByteArray, outTokens: MutableList<Int>) {
        val n = pieceBytes.size
        if (n == 0) return
        if (n == 1) {
            val rank = ranks[ByteArrayKey(pieceBytes, 0, 1)]
                ?: throw IllegalStateException("Missing fallback rank for byte 0x${"%02X".format(pieceBytes[0])}")
            outTokens.add(rank)
            return
        }

        val starts = IntArray(n) { it }
        val lens = IntArray(n) { 1 }
        val next = IntArray(n) { it + 1 }
        val prev = IntArray(n) { it - 1 }
        next[n - 1] = -1

        var activeCount = n

        while (activeCount > 1) {
            var minRank = Int.MAX_VALUE
            var bestIdx = -1

            var curr = 0
            while (curr != -1) {
                val nxt = next[curr]
                if (nxt == -1) break

                val key = ByteArrayKey(pieceBytes, starts[curr], lens[curr] + lens[nxt])
                val rank = ranks[key]
                if (rank != null && rank < minRank) {
                    minRank = rank
                    bestIdx = curr
                }
                curr = nxt
            }

            if (minRank == Int.MAX_VALUE || bestIdx == -1) break

            val nxt = next[bestIdx]
            lens[bestIdx] += lens[nxt]

            // Remove nxt from doubly-linked list: O(1)
            val afterNxt = next[nxt]
            next[bestIdx] = afterNxt
            if (afterNxt != -1) {
                prev[afterNxt] = bestIdx
            }
            activeCount--
        }

        var curr = 0
        while (curr != -1) {
            val key = ByteArrayKey(pieceBytes, starts[curr], lens[curr])
            val rank = ranks[key]
                ?: throw IllegalStateException("Failed BPE rank resolution for byte piece length ${lens[curr]}")
            outTokens.add(rank)
            curr = next[curr]
        }
    }

    /**
     * Decodes a single token ID into its exact raw byte representation.
     * Rejects invalid token IDs explicitly.
     */
    fun decodeToken(tokenId: Int): ByteArray {
        if (tokenId !in 0 until TOTAL_VOCAB_SIZE) {
            throw IllegalArgumentException("Invalid token ID: $tokenId (valid range: 0..${TOTAL_VOCAB_SIZE - 1})")
        }
        return tokenToBytes[tokenId] ?: throw IllegalStateException("Unmapped token ID: $tokenId")
    }

    /**
     * Decodes a complete token sequence to UTF-8 string with O(1) array lookups.
     */
    fun decode(tokens: List<Int>): String {
        val baos = ByteArrayOutputStream(tokens.size * 3)
        for (tok in tokens) {
            baos.write(decodeToken(tok))
        }
        return baos.toString(StandardCharsets.UTF_8)
    }

    fun countTokens(text: String?): Int {
        return encode(text).size
    }

    /**
     * Creates a new stateful UTF-8 streaming decoder for handling generation callbacks
     * across multi-byte token boundaries.
     */
    fun createStatefulStreamingDecoder(): StatefulUtf8Decoder {
        return StatefulUtf8Decoder(this)
    }

    /**
     * Stateful UTF-8 Decoder preserving incomplete multi-byte sequences between tokens.
     */
    class StatefulUtf8Decoder(private val adapter: Llama3TikTokenAdapter) {
        private var residualBytes = ByteArray(0)
        private val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        fun decodeTokenDelta(tokenId: Int): String {
            val rawBytes = adapter.decodeToken(tokenId)
            return decodeByteDelta(rawBytes)
        }

        @Synchronized
        fun decodeByteDelta(newBytes: ByteArray): String {
            val totalBytes = if (residualBytes.isNotEmpty()) {
                val combined = ByteArray(residualBytes.size + newBytes.size)
                System.arraycopy(residualBytes, 0, combined, 0, residualBytes.size)
                System.arraycopy(newBytes, 0, combined, residualBytes.size, newBytes.size)
                combined
            } else {
                newBytes
            }

            if (totalBytes.isEmpty()) return ""

            // Find valid decodable prefix
            val bb = ByteBuffer.wrap(totalBytes)
            val cb = CharBuffer.allocate(totalBytes.size * 2)

            val cr = decoder.decode(bb, cb, false)
            if (cr.isError) {
                // Malformed sequence fallback: skip corrupted byte safely
                bb.position(bb.position() + 1)
            }

            cb.flip()
            val emittedText = cb.toString()

            val remaining = bb.remaining()
            residualBytes = if (remaining > 0) {
                val trailing = ByteArray(remaining)
                bb.get(trailing)
                trailing
            } else {
                ByteArray(0)
            }

            return emittedText
        }

        @Synchronized
        fun flush(): String {
            if (residualBytes.isEmpty()) return ""
            val bb = ByteBuffer.wrap(residualBytes)
            val cb = CharBuffer.allocate(residualBytes.size * 2)
            decoder.decode(bb, cb, true)
            decoder.flush(cb)
            cb.flip()
            residualBytes = ByteArray(0)
            return cb.toString()
        }
    }
}
