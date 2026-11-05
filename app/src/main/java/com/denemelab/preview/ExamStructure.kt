package com.denemelab.preview

/**
 * 2026 ÖSYM YKS test structure. AYT is entered as an 80-question
 * score-type subset, NOT falsely described as the entire AYT booklet
 * (which contains 160 questions). Net = correct - wrong/4.
 *
 * Edebiyat-Sosyal 1, Fen and Sosyal 2 are split into actual subtests
 * to make individual weaknesses visible. Optional YDT/other combinations
 * will be modeled in a later version; unsupported combinations are
 * deliberately not silently scored.
 */
data class SubjectTemplate(
    val key: String,
    val title: String,
    val questions: Int
)

data class SubjectScore(
    val key: String,
    val title: String,
    val questions: Int,
    val correct: Int,
    val wrong: Int
) {
    val blank: Int get() = questions - correct - wrong
    val net: Double get() = ExamRules.net(correct, wrong)
}

object ExamStructure {
    val tyt: List<SubjectTemplate> = listOf(
        SubjectTemplate("tyt_turkce", "Türkçe", 40),
        SubjectTemplate("tyt_sosyal", "Sosyal Bilimler", 20),
        SubjectTemplate("tyt_matematik", "Temel Matematik", 40),
        SubjectTemplate("tyt_fen", "Fen Bilimleri", 20)
    )
    private val mat = SubjectTemplate("ayt_mat", "Matematik", 40)
    private val physics = SubjectTemplate("ayt_fiz", "Fizik", 14)
    private val chemistry = SubjectTemplate("ayt_kim", "Kimya", 13)
    private val biology = SubjectTemplate("ayt_biy", "Biyoloji", 13)
    private val literature = SubjectTemplate("ayt_edeb", "Türk Dili ve Edebiyatı", 24)
    private val history1 = SubjectTemplate("ayt_tar1", "Tarih-1", 10)
    private val geography1 = SubjectTemplate("ayt_cog1", "Coğrafya-1", 6)
    private val history2 = SubjectTemplate("ayt_tar2", "Tarih-2", 11)
    private val geography2 = SubjectTemplate("ayt_cog2", "Coğrafya-2", 11)
    private val philosophy = SubjectTemplate("ayt_fels", "Felsefe Grubu", 12)
    private val religion = SubjectTemplate("ayt_din", "Din / Ek Felsefe", 6)

    fun subjects(examType: String, track: String): List<SubjectTemplate> = when (examType) {
        "TYT" -> tyt
        "AYT" -> when (track) {
            "SAY" -> listOf(mat, physics, chemistry, biology)
            "EA" -> listOf(mat, literature, history1, geography1)
            "SÖZ" -> listOf(literature, history1, geography1, history2, geography2, philosophy, religion)
            else -> emptyList()
        }
        else -> emptyList()
    }

    fun valid(scores: List<SubjectScore>, examType: String, track: String): Boolean {
        val required = subjects(examType, track)
        if (required.isEmpty() || scores.size != required.size) return false
        return required.zip(scores).all { (template, value) ->
            value.key == template.key &&
                value.title == template.title &&
                value.questions == template.questions &&
                value.correct >= 0 && value.wrong >= 0 &&
                value.correct.toLong() + value.wrong.toLong() <= template.questions
        }
    }

    fun correct(scores: List<SubjectScore>): Int = scores.sumOf { it.correct }
    fun wrong(scores: List<SubjectScore>): Int = scores.sumOf { it.wrong }
    fun blank(scores: List<SubjectScore>): Int = scores.sumOf { it.blank }
    fun net(scores: List<SubjectScore>): Double = scores.sumOf { it.net }

    fun comparable(first: ExamRecord, second: ExamRecord): Boolean {
        if (first.category != second.category || first.examType != second.examType) return false
        if (first.category == "Branş")
            return first.subject.trim().equals(second.subject.trim(), ignoreCase = true)
        return first.examType != "AYT" || first.track == second.track
    }
}
