package com.denemelab.preview

object ExamRules {
    fun net(correct: Int, wrong: Int): Double = correct - wrong / 4.0

    fun validCounts(
        category: String,
        examType: String,
        subject: String,
        correct: Int?,
        wrong: Int?,
        blank: Int?
    ): Boolean {
        if (correct == null || wrong == null || blank == null) return false
        if (correct < 0 || wrong < 0 || blank < 0) return false
        val total = correct.toLong() + wrong.toLong() + blank.toLong()
        if (total == 0L || total > 120L) return false
        if (category == "Genel") {
            val expected = when (examType) {
                "TYT" -> 120L
                "AYT" -> 80L
                else -> return false
            }
            return total == expected
        }
        return category == "Branş" && subject.isNotBlank()
    }

    fun comparable(first: ExamRecord, second: ExamRecord): Boolean =
        ExamStructure.comparable(first, second)

    fun nextCorrectStreak(current: Int, correct: Boolean): Int =
        if (correct) (current + 1).coerceIn(0, 2) else 0
}
