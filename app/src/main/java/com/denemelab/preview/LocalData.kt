package com.denemelab.preview

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExamRecord(
    val id: Long,
    val name: String,
    val category: String,
    val examType: String,
    val subject: String,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val createdAt: Long,
    val track: String = "",
    val parts: List<SubjectScore> = emptyList(),
    val publisher: String = "",
    val examDate: Long = createdAt,
    val mood: String = "Normal",
    val topicIssues: List<ExamTopicIssue> = emptyList(),
    val source: String = "Manuel"
) {
    val net: Double get() = ExamRules.net(correct, wrong)
    val hasSubjectDetails: Boolean get() = parts.isNotEmpty()
}

data class QuestionRecord(
    val id: Long,
    val title: String,
    val subject: String,
    val topic: String,
    val source: String,
    val outcome: String,
    val correctStreak: Int,
    val createdAt: Long
) {
    val completed: Boolean get() = correctStreak >= 2
}

data class TargetRecord(
    val university: String = "",
    val department: String = "",
    val targetRank: String = "",
    val schoolGrade: String = "",
    val programCode: Long = 0L,
    val universityType: String = "",
    val language: String = "",
    val scholarship: String = "",
    val scoreType: String = ""
)

data class StudentProfile(
    val firstName: String = "",
    val surname: String = "",
    val grade: String = "Seçilmedi",
    val pictureUri: String = ""
) {
    val displayName: String get() = firstName.ifBlank { "Öğrenci" }
}


object LocalData {
    private const val PREFS = "denemelab_local_v1"
    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadExams(context: Context): List<ExamRecord> = runCatching {
        ExamJsonCodec.decode(preferences(context).getString("exams", "[]") ?: "[]")
    }.getOrElse { emptyList() }

    /**
     * Synchronous write allows callers to report a storage failure rather
     * than claiming that a record has been saved before disk persistence.
     * Existing v0.3.x preference key remains unchanged (in-place upgrade).
     */
    fun saveExams(context: Context, exams: List<ExamRecord>): Boolean =
        preferences(context).edit().putString("exams", ExamJsonCodec.encode(exams)).commit()

    fun loadQuestions(context: Context): List<QuestionRecord> = runCatching {
        val values = JSONArray(preferences(context).getString("questions", "[]") ?: "[]")
        (0 until values.length()).map { i ->
            val v = values.getJSONObject(i)
            QuestionRecord(
                id = v.getLong("id"),
                title = v.optString("title"),
                subject = v.optString("subject"),
                topic = v.optString("topic"),
                source = v.optString("source"),
                outcome = v.optString("outcome", "Yanlış"),
                correctStreak = v.optInt("correctStreak", 0),
                createdAt = v.getLong("createdAt")
            )
        }.sortedByDescending { it.createdAt }
    }.getOrElse { emptyList() }

    fun saveQuestions(context: Context, questions: List<QuestionRecord>) {
        val values = JSONArray()
        questions.forEach {
            values.put(JSONObject()
                .put("id", it.id)
                .put("title", it.title)
                .put("subject", it.subject)
                .put("topic", it.topic)
                .put("source", it.source)
                .put("outcome", it.outcome)
                .put("correctStreak", it.correctStreak)
                .put("createdAt", it.createdAt))
        }
        preferences(context).edit().putString("questions", values.toString()).apply()
    }

    fun loadTarget(context: Context): TargetRecord {
        val p = preferences(context)
        return TargetRecord(
            university = p.getString("university", "") ?: "",
            department = p.getString("department", "") ?: "",
            targetRank = p.getString("targetRank", "") ?: "",
            schoolGrade = p.getString("schoolGrade", "") ?: "",
            programCode = p.getLong("programCode", 0L),
            universityType = p.getString("universityType", "") ?: "",
            language = p.getString("language", "") ?: "",
            scholarship = p.getString("scholarship", "") ?: "",
            scoreType = p.getString("scoreType", "") ?: ""
        )
    }

    fun saveTarget(context: Context, target: TargetRecord) {
        preferences(context).edit()
            .putString("university", target.university)
            .putString("department", target.department)
            .putString("targetRank", target.targetRank)
            .putString("schoolGrade", target.schoolGrade)
            .putLong("programCode", target.programCode)
            .putString("universityType", target.universityType)
            .putString("language", target.language)
            .putString("scholarship", target.scholarship)
            .putString("scoreType", target.scoreType)
            .apply()
    }

    fun loadProfile(context: Context): StudentProfile {
        val p = preferences(context)
        return StudentProfile(
            firstName = p.getString("studentFirstName", "") ?: "",
            surname = p.getString("studentSurname", "") ?: "",
            grade = p.getString("studentGrade", "Seçilmedi") ?: "Seçilmedi",
            pictureUri = p.getString("studentPictureUri", "") ?: ""
        )
    }

    fun saveProfile(context: Context, profile: StudentProfile) {
        preferences(context).edit()
            .putString("studentFirstName", profile.firstName)
            .putString("studentSurname", profile.surname)
            .putString("studentGrade", profile.grade)
            .putString("studentPictureUri", profile.pictureUri)
            .apply()
    }

    fun reduceMotion(context: Context): Boolean =
        preferences(context).getBoolean("reduceMotion", false)

    fun saveReduceMotion(context: Context, value: Boolean) {
        preferences(context).edit().putBoolean("reduceMotion", value).apply()
    }

    fun date(millis: Long): String = SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR"))
        .format(Date(millis))

    fun month(millis: Long): String = SimpleDateFormat("MMM yyyy", Locale("tr", "TR"))
        .format(Date(millis))

    fun monthKey(millis: Long): String = SimpleDateFormat("yyyy-MM", Locale.ROOT)
        .format(Date(millis))

    fun net(value: Double): String = String.format(Locale("tr", "TR"), "%.2f", value)
}
