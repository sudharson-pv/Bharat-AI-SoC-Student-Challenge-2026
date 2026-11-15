package com.learnmate.ai.content

import com.learnmate.ai.data.content.CorruptDocumentException
import com.learnmate.ai.data.content.DocumentIngestionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate 6.16.1 Priority 5: Comprehensive Physical & Logic Validation of Document Ingestion Pipeline.
 *
 * Verifies all 8 document types & edge cases:
 * 1. Normal Digital PDF (%PDF- header, text streams, high confidence ~0.96f)
 * 2. Scanned PDF (%PDF- header, no text streams, calibrated OCR confidence = 0.82f)
 * 3. Corrupt PDF (missing %PDF- header, fails closed with CorruptDocumentException)
 * 4. JPEG Image (Image OCR pipeline, calibrated confidence 0.88f-0.94f)
 * 5. PNG Image (Image OCR pipeline, calibrated confidence 0.88f-0.94f)
 * 6. Plain Text (Digital UTF-8 text, 1.0f confidence)
 * 7. Markdown Text (Digital text with markdown heading hierarchy, 1.0f confidence)
 * 8. Multilingual Unicode Extraction (Tamil, Hindi, Kannada, English heading detection)
 * 9. Large Document Bounded Paging (Streaming without memory growth)
 */
class DocumentIngestionComprehensiveTest {

    @Test
    fun test1_NormalDigitalPdf() = runBlocking {
        val pdfRaw = """
            %PDF-1.4
            1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
            2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
            3 0 obj << /Type /Page /Parent 2 0 R /Contents 4 0 R >> endobj
            4 0 obj << /Length 120 >>
            stream
            Chapter 1: Principles of Quantum Computing
            Unit 1: Superposition and Qubits
            This section covers foundational physical principles of quantum gates.
            endstream
            endobj
            xref
            trailer << /Root 1 0 R >>
            %%EOF
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

        val result = DocumentIngestionEngine.stageDocumentFromBytes(
            rawBytes = pdfRaw,
            fileName = "Quantum_Computing_Syllabus.pdf",
            mimeType = "application/pdf"
        )

        assertTrue("Normal PDF should succeed", result.isSuccess)
        val staging = result.getOrThrow()
        assertEquals("application/pdf", staging.mimeType)
        assertTrue("Digital PDF confidence should be ~0.96f", staging.overallConfidence >= 0.95f)
        assertTrue("Should extract chapters/units", staging.extractedNodes.isNotEmpty())
        assertEquals("Quantum_Computing_Syllabus.pdf", staging.fileName)
    }

    @Test
    fun test2_ScannedPdfWithoutTextStreams() = runBlocking {
        val scannedPdfRaw = """
            %PDF-1.5
            1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
            2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
            3 0 obj << /Type /Page /Parent 2 0 R /Resources << /XObject << /Im1 4 0 R >> >> >> endobj
            4 0 obj << /Type /XObject /Subtype /Image /Width 800 /Height 1200 /Filter /DCTDecode /Length 12 >>
            stream
            RAWIMAGEBYTES
            endstream
            endobj
            %%EOF
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

        val result = DocumentIngestionEngine.stageDocumentFromBytes(
            rawBytes = scannedPdfRaw,
            fileName = "Scanned_Lecture_Notes.pdf",
            mimeType = "application/pdf"
        )

        assertTrue("Scanned PDF should succeed via OCR fallback", result.isSuccess)
        val staging = result.getOrThrow()
        assertEquals(0.82f, staging.overallConfidence, 0.01f)
        assertTrue("Page text should indicate scanned OCR processing",
            staging.pages.first().text.contains("[Scanned PDF - OCR Processed"))
    }

    @Test
    fun test3_CorruptPdfRejectedFailClosed() = runBlocking {
        val corruptedBytes = "NOT A VALID PDF FILE AT ALL".toByteArray()

        val result = DocumentIngestionEngine.stageDocumentFromBytes(
            rawBytes = corruptedBytes,
            fileName = "MaliciousOrCorrupt.pdf",
            mimeType = "application/pdf"
        )

        assertTrue("Corrupt PDF without %PDF- must fail", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Must throw CorruptDocumentException", exception is CorruptDocumentException)
        assertTrue(exception?.message?.contains("missing '%PDF-' header signature") == true)
    }

    @Test
    fun test4_JpegImageOcr() = runBlocking {
        val fakeJpegBytes = ByteArray(60_000) { 0x7F }

        val result = DocumentIngestionEngine.stageDocumentFromBytes(
            rawBytes = fakeJpegBytes,
            fileName = "organic_chemistry_diagrams.jpg",
            mimeType = "image/jpeg"
        )

        assertTrue("JPEG ingestion should succeed", result.isSuccess)
        val staging = result.getOrThrow()
        assertTrue("Image OCR confidence should be calibrated >= 0.88f", staging.overallConfidence >= 0.88f)
        assertEquals(1, staging.totalPages)
        assertTrue(staging.extractedNodes.isNotEmpty())
    }

    @Test
    fun test5_PngImageOcr() = runBlocking {
        val fakePngBytes = ByteArray(30_000) { 0x55 }

        val result = DocumentIngestionEngine.stageDocumentFromBytes(
            rawBytes = fakePngBytes,
            fileName = "circuit_schematics.png",
            mimeType = "image/png"
        )

        assertTrue("PNG ingestion should succeed", result.isSuccess)
        val staging = result.getOrThrow()
        assertTrue("PNG confidence should be in 0.88f..0.94f", staging.overallConfidence in 0.88f..0.94f)
        assertEquals(1, staging.totalPages)
    }

    @Test
    fun test6_PlainTextIngestion() = runBlocking {
        val text = """
            Chapter 1: Introduction to Environmental Science
            Ecosystems and biological equilibrium.
            Chapter 2: Atmospheric Dynamics
            Thermodynamic cycles and climate models.
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val result = DocumentIngestionEngine.stageDocumentFromBytes(
            rawBytes = text,
            fileName = "environmental_notes.txt",
            mimeType = "text/plain"
        )

        assertTrue("Text ingestion should succeed", result.isSuccess)
        val staging = result.getOrThrow()
        assertEquals(1.0f, staging.overallConfidence, 0.001f)
        assertEquals(2, staging.extractedNodes.size)
        assertEquals("Chapter 1: Introduction to Environmental Science", staging.extractedNodes[0].title)
        assertEquals("Chapter 2: Atmospheric Dynamics", staging.extractedNodes[1].title)
    }

    @Test
    fun test7_MarkdownHierarchyIngestion() = runBlocking {
        val md = """
            # Modern Operating Systems
            ## Kernel Architecture and Memory Management
            Paging, segmentation, and translation lookaside buffers.
            ## Process Scheduling and Concurrency
            Preemptive vs cooperative multitasking.
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val result = DocumentIngestionEngine.stageDocumentFromBytes(
            rawBytes = md,
            fileName = "os_syllabus.md",
            mimeType = "text/markdown"
        )

        assertTrue("Markdown ingestion should succeed", result.isSuccess)
        val staging = result.getOrThrow()
        assertEquals(1.0f, staging.overallConfidence, 0.001f)
        val titles = staging.extractedNodes.map { it.title }
        assertTrue(titles.contains("Modern Operating Systems"))
        assertTrue(titles.contains("Kernel Architecture and Memory Management"))
        assertTrue(titles.contains("Process Scheduling and Concurrency"))
    }

    @Test
    fun test8_MultilingualUnicodeHeadingExtraction() {
        val multilingualText = """
            Chapter 1: Fundamental Mechanics
            1.1 Kinetic and Potential Energy
            
            அத்தியாயம் 2: மின் காந்தவியல் கோட்பாடு
            அலகு 1: மேக்ஸ்வெல் சமன்பாடுகள்
            
            अध्याय 3: कार्बनिक रसायन विज्ञान
            इकाई 1: हाइड्रोकार्बन और रासायनिक प्रतिक्रियाएं
            
            ಅಧ್ಯಾಯ 4: ಕೃತಕ ಬುದ್ಧಿಮತ್ತೆ ಮತ್ತು ಗಣಕ ವಿಜ್ಞಾನ
            ಘಟಕ 1: ತಾರ್ಕಿಕ ಕಲಿಕೆ ಮತ್ತು ಯಂತ್ರಜ್ಞಾನ
        """.trimIndent()

        val headings = DocumentIngestionEngine.extractHeadingsFromText(multilingualText)

        // Verify English
        assertTrue(headings.any { it.contains("Fundamental Mechanics") })
        assertTrue(headings.any { it.contains("Kinetic and Potential Energy") })

        // Verify Tamil
        assertTrue(headings.any { it.contains("அத்தியாயம் 2: மின் காந்தவியல் கோட்பாடு") })
        assertTrue(headings.any { it.contains("அலகு 1: மேக்ஸ்வெல் சமன்பாடுகள்") })

        // Verify Hindi
        assertTrue(headings.any { it.contains("अध्याय 3: कार्बनिक रसायन विज्ञान") })
        assertTrue(headings.any { it.contains("इकाई 1: हाइड्रोकार्बन और रासायनिक प्रतिक्रियाएं") })

        // Verify Kannada
        assertTrue(headings.any { it.contains("ಅಧ್ಯಾಯ 4: ಕೃತಕ ಬುದ್ಧಿಮತ್ತೆ ಮತ್ತು ಗಣಕ ವಿಜ್ಞಾನ") })
        assertTrue(headings.any { it.contains("ಘಟಕ 1: ತಾರ್ಕಿಕ ಕಲಿಕೆ ಮತ್ತು ಯಂತ್ರಜ್ಞಾನ") })
    }

    @Test
    fun test9_LargeDocumentStreamingPagination() {
        // Generate a 100,000-character syllabus document
        val sb = StringBuilder()
        for (i in 1..50) {
            sb.append("Chapter $i: Advanced Engineering Systems Module $i\n")
            sb.append("Detailed mathematical formulations, proofs, and laboratory practicals for semester module $i.\n")
            sb.append("A".repeat(1800)).append("\n\n")
        }
        val largeText = sb.toString()

        val pages = DocumentIngestionEngine.paginateText(largeText, charsPerPage = 1800, maxPages = 100)
        assertTrue("Should paginate into multiple pages", pages.size > 10)
        assertTrue("Should not exceed max pages limit", pages.size <= 100)
        // Memory stays bounded
        pages.forEach { page ->
            assertTrue(page.length <= 1800)
        }
    }
}

