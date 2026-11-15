package com.learnmate.ai.content

import com.learnmate.ai.data.content.DocumentIngestionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentIngestionEngineTest {

    @Test
    fun testUserCorrectionInPlaceRefinement() {
        val draftNodes = mutableListOf(
            DocumentIngestionEngine.ExtractedCurriculumNodeDraft(
                tempId = "draft_1",
                title = "Raw Extracted Header",
                nodeType = "TOPIC",
                description = "Initial draft",
                estimatedMinutes = 30,
                sourcePageStart = 1,
                sourcePageEnd = 2,
                isApproved = true
            )
        )

        val staging = DocumentIngestionEngine.IngestedDocumentStaging(
            documentId = "doc_test_101",
            fileName = "Syllabus_Physics.pdf",
            mimeType = "application/pdf",
            fileSize = 102400L,
            totalPages = 2,
            overallConfidence = 0.95f,
            pages = emptyList(),
            extractedNodes = draftNodes,
            checksumSha256 = "abcd1234abcd1234"
        )

        // Perform user correction
        val updated = DocumentIngestionEngine.updateDraftNode(
            staging = staging,
            tempId = "draft_1",
            newTitle = "Chapter 1: Classical Mechanics",
            newDescription = "Newtonian physics and conservation of momentum.",
            newNodeType = "CHAPTER",
            newMinutes = 60,
            isApproved = true
        )

        assertTrue(updated)
        val node = staging.extractedNodes[0]
        assertEquals("Chapter 1: Classical Mechanics", node.title)
        assertEquals("Newtonian physics and conservation of momentum.", node.description)
        assertEquals("CHAPTER", node.nodeType)
        assertEquals(60, node.estimatedMinutes)
        assertTrue(node.isApproved)
    }

    @Test
    fun testUserRejectionOfDraftNode() {
        val draftNodes = mutableListOf(
            DocumentIngestionEngine.ExtractedCurriculumNodeDraft(
                tempId = "draft_2",
                title = "Advertisement / Preface",
                nodeType = "TOPIC",
                description = "Extracted noise",
                estimatedMinutes = 15,
                sourcePageStart = 1,
                sourcePageEnd = 1,
                isApproved = true
            )
        )

        val staging = DocumentIngestionEngine.IngestedDocumentStaging(
            documentId = "doc_test_102",
            fileName = "Sample.pdf",
            mimeType = "application/pdf",
            fileSize = 51200L,
            totalPages = 1,
            overallConfidence = 0.90f,
            pages = emptyList(),
            extractedNodes = draftNodes,
            checksumSha256 = "ef012345ef012345"
        )

        // User unapproves the noise node
        DocumentIngestionEngine.updateDraftNode(
            staging = staging,
            tempId = "draft_2",
            newTitle = "Advertisement / Preface",
            newDescription = "Extracted noise",
            isApproved = false
        )

        assertFalse(staging.extractedNodes[0].isApproved)
    }
}

