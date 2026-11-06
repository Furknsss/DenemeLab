package com.denemelab.preview

/**
 * A row-level reliability gate. Recognised topic rows are never evidence for
 * unseen questions. "Not read" must not become "correct" in Analysis.
 *
 * Unlike the older validOutcomes() check this also verifies *equality* of
 * correct, wrong and blank, and therefore catches 39 / 120 OCR coverage.
 */
object ReportCoverage {
    data class Course(
        val key: String,
        val name: String,
        val total: Int,
        val observed: Int,
        val remainingCorrect: Int,
        val remainingWrong: Int,
        val remainingBlank: Int,
        val valid: Boolean
    ) {
        val missing: Int get() = (total - observed).coerceAtLeast(0)
        val complete: Boolean get() =
            valid && observed == total &&
                remainingCorrect == 0 && remainingWrong == 0 &&
                remainingBlank == 0
    }
    data class Audit(
        val courses: List<Course>,
        val valid: Boolean,
        val complete: Boolean
    ) {
        val observed: Int get() = courses.sumOf { it.observed }
        val total: Int get() = courses.sumOf { it.total }
        val missing: Int get() = courses.sumOf { it.missing }
        val incompleteCourses: List<Course> get() = courses.filterNot { it.complete }
    }

    fun inspect(parts: List<SubjectScore>, outcomes: List<TopicOutcome>): Audit {
        val keys = parts.map { it.key }.toSet()
        val duplicate = outcomes.groupBy {
            TopicAnalyticsV3.identity(it.lessonKey, it.topic)
        }.any { it.value.size > 1 }
        val unknown = outcomes.any { it.lessonKey !in keys ||
            it.topic.isBlank() || it.correct < 0 || it.wrong < 0 ||
            it.blank < 0 || it.questions < 1
        }
        val courses = parts.map { course ->
            val rows = outcomes.filter { it.lessonKey == course.key }
            val right = rows.sumOf { it.correct }
            val wrong = rows.sumOf { it.wrong }
            val blank = rows.sumOf { it.blank }
            val valid = right <= course.correct &&
                wrong <= course.wrong && blank <= course.blank &&
                rows.none { it.questions <= 0 }
            Course(course.key, course.title, course.questions,
                right + wrong + blank,
                course.correct - right, course.wrong - wrong,
                course.blank - blank, valid)
        }
        val valid = !duplicate && !unknown && courses.all { it.valid }
        return Audit(courses, valid, valid && courses.all { it.complete })
    }
}
