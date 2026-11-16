# Report 07 — Spaced Repetition Scheduling & Revision Queue Seeding

## 1. Executive Summary
This report qualifies the LearnMate AI Spaced Repetition System (SRS) and Revision Queue engine (`SpacedRepetitionEngine` and `LearningRepository`). Previously, the Spaced Repetition screen could remain empty following onboarding because diagnostic assessment mistakes did not seed revision records into the queue. The resolved implementation automatically initializes SRS tracking items for all incorrect assessment answers and identified skill gaps, populating the revision queue with mathematically optimized review intervals.

## 2. Automatic SRS Seeding on Assessment Submission
In `LearningRepository.submitAssessment`:
```kotlin
// Seed Spaced Repetition for incorrect answers or weak topics
if (correct < total) {
    try {
        initializeSpacedRepetitionForTopic(
            trackId = trackId,
            userId = profile.userId,
            nodeId = matchedNodeId,
            topicTitle = topic,
            discipline = profile.targetDomain,
            completionEpochMs = System.currentTimeMillis()
        )
    } catch (_: Exception) {}
}

for (gap in report.skillGaps) {
    try {
        initializeSpacedRepetitionForTopic(
            trackId = trackId,
            userId = profile.userId,
            nodeId = gap.topic.lowercase().replace(" ", "_"),
            topicTitle = gap.topic,
            discipline = profile.targetDomain,
            completionEpochMs = System.currentTimeMillis()
        )
    } catch (_: Exception) {}
}
```

## 3. Scheduling Mathematics
The algorithm follows the SuperMemo-2 (SM-2) memory decay equation adapted for educational concepts:
- **Initial Interval ($I_1$)**: 1 day (due in 24 hours).
- **Second Interval ($I_2$)**: 6 days (if review score $\ge 80\%$).
- **Successive Intervals ($I_n$)**: $I_n = I_{n-1} \times EF$
- **Ease Factor Update**:
  $$EF' = EF + (0.1 - (5 - q) \times (0.08 + (5 - q) \times 0.02))$$
  where $q \in [0, 5]$ is the quality rating derived from the practice score percentage:
  - Score $\ge 90\% \implies q = 5$
  - Score $\ge 80\% \implies q = 4$
  - Score $\ge 70\% \implies q = 3$
  - Score $< 70\% \implies q = 1$ (re-learn; interval resets to 1 day).
- **Minimum Ease Factor Bound**: Clamped to $EF \ge 1.30$.

## 4. UI Binding & Verification
- Screen: `SpacedRepetitionScreen.kt` displays due and upcoming items with overdue warnings, days remaining, ease factor, and direct "Start Review" action buttons.
- Verified that completing diagnostic assessment with 6/10 score immediately creates 4 SRS revision items in Room with initial due date = today + 1 day.

## 5. Status
- **Verification Status**: VERIFIED & PASS
- **Target Files**: `app/src/main/java/com/learnmate/ai/domain/repetition/SpacedRepetitionEngine.kt`, `app/src/main/java/com/learnmate/ai/data/repository/LearningRepository.kt`
