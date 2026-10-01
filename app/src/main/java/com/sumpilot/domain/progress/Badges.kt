package com.sumpilot.domain.progress

import com.sumpilot.domain.model.Operation
import com.sumpilot.domain.sessions.ProgressTotals

/** Participation-based badges. None require accuracy, speed, streaks or attendance. */
enum class Badge(val key: String, val title: String, val description: String) {
    FIRST_FLIGHT("first_flight", "First Flight", "Answer one question."),
    MISSION_COMPLETE("mission_complete", "Mission Complete", "Finish one 10-question mission."),
    FOUR_DIRECTIONS("four_directions", "Four Directions", "Answer a question in each operation."),
    FIFTY_PRACTICED("fifty_practiced", "Fifty Practiced", "Answer 50 mission questions."),
    CAREFUL_RETURN("careful_return", "Careful Return", "Get a mistake right on a retry."),
    FIVE_MISSIONS("five_missions", "Five Missions", "Finish five missions.");

    companion object {
        fun fromKey(key: String): Badge? = entries.firstOrNull { it.key == key }
    }
}

object BadgeRules {
    fun earned(t: ProgressTotals): Set<Badge> = buildSet {
        if (t.originalAnswered >= 1) add(Badge.FIRST_FLIGHT)
        if (t.completedTenQuestionSessions >= 1) add(Badge.MISSION_COMPLETE)
        if (Operation.entries.all { t.answeredFor(it) >= 1 }) add(Badge.FOUR_DIRECTIONS)
        if (t.originalAnswered >= 50) add(Badge.FIFTY_PRACTICED)
        if (t.resolvedMistakes >= 1) add(Badge.CAREFUL_RETURN)
        if (t.completedSessions >= 5) add(Badge.FIVE_MISSIONS)
    }

    /** Badges that should be unlocked now and are not yet stored. */
    fun newlyEarned(t: ProgressTotals, alreadyUnlocked: Set<String>): List<Badge> =
        earned(t).filter { it.key !in alreadyUnlocked }.sortedBy { it.ordinal }
}
