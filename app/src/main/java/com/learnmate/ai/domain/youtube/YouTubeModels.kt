package com.learnmate.ai.domain.youtube

import com.google.gson.annotations.SerializedName
import com.learnmate.ai.domain.AssessmentQuestion

/**
 * YouTube Video Item domain model representing curated video lectures for learners.
 */
data class YouTubeVideoItem(
    val id: String,
    val title: String,
    val description: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val publishedAt: String = "",
    val durationIso: String = "",
    val durationFormatted: String = "",
    val topic: String = "",
    val playbackUrl: String = "https://www.youtube.com/watch?v=$id",
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val isOfflineCached: Boolean = false,
    val cachedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * Study notes and check-for-understanding questions synthesized by local Llama 3.2 1B
 * from video context or authorized captions/transcripts.
 */
data class VideoStudyNotes(
    val videoId: String,
    val videoTitle: String,
    val topic: String,
    val summary: String,
    val keyTakeaways: List<String>,
    val generatedQuestions: List<AssessmentQuestion> = emptyList(),
    val sourceProvenance: String = "Local On-Device Meta Llama 3.2 1B (ExecuTorch 1.4.0)",
    val generatedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * YouTube API Error Representation
 */
data class YouTubeApiError(
    val httpCode: Int,
    override val message: String,
    val reason: String = "UNKNOWN",
    val isQuotaExceeded: Boolean = false,
    val isNetworkError: Boolean = false
) : Exception("YouTube API Error ($httpCode): $message [reason: $reason]")

// ── JSON DTOs for YouTube Data API v3 ─────────────────────────────────────────

data class YouTubeSearchListResponse(
    val kind: String?,
    val etag: String?,
    val nextPageToken: String?,
    val regionCode: String?,
    val pageInfo: PageInfo?,
    val items: List<YouTubeSearchResultItem>?
)

data class PageInfo(
    val totalResults: Int?,
    val resultsPerPage: Int?
)

data class YouTubeSearchResultItem(
    val kind: String?,
    val etag: String?,
    val id: YouTubeResourceId?,
    val snippet: YouTubeSnippet?
)

data class YouTubeResourceId(
    val kind: String?,
    val videoId: String?
)

data class YouTubeSnippet(
    val publishedAt: String?,
    val channelId: String?,
    val title: String?,
    val description: String?,
    val thumbnails: YouTubeThumbnails?,
    val channelTitle: String?,
    val liveBroadcastContent: String?
)

data class YouTubeThumbnails(
    val default: YouTubeThumbnailInfo?,
    val medium: YouTubeThumbnailInfo?,
    val high: YouTubeThumbnailInfo?,
    val standard: YouTubeThumbnailInfo?,
    val maxres: YouTubeThumbnailInfo?
)

data class YouTubeThumbnailInfo(
    val url: String?,
    val width: Int?,
    val height: Int?
)

data class YouTubeVideoListResponse(
    val kind: String?,
    val etag: String?,
    val items: List<YouTubeVideoDetailsItem>?
)

data class YouTubeVideoDetailsItem(
    val id: String?,
    val snippet: YouTubeSnippet?,
    val contentDetails: YouTubeContentDetails?,
    val statistics: YouTubeStatistics?
)

data class YouTubeContentDetails(
    val duration: String?,
    val dimension: String?,
    val definition: String?,
    val caption: String?
)

data class YouTubeStatistics(
    val viewCount: String?,
    val likeCount: String?,
    val favoriteCount: String?,
    val commentCount: String?
)

data class YouTubeErrorResponse(
    val error: YouTubeErrorPayload?
)

data class YouTubeErrorPayload(
    val code: Int?,
    val message: String?,
    val errors: List<YouTubeErrorDetail>?,
    val status: String?
)

data class YouTubeErrorDetail(
    val message: String?,
    val domain: String?,
    val reason: String?
)
