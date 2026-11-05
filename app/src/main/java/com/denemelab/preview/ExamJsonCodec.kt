package com.denemelab.preview

import org.json.JSONArray
import org.json.JSONObject

/**
 * Backward-compatible serialization of DenemeLab exams.
 * Old records contain only totals and are retained exactly, without
 * fabricating subject breakdowns. New records persist validated subtests.
 */
object ExamJsonCodec {
    fun encode(exams: List<ExamRecord>): String {
        val arr = JSONArray()
        exams.forEach { exam ->
            val record = JSONObject()
                .put("id", exam.id).put("name", exam.name)
                .put("category", exam.category).put("examType", exam.examType)
                .put("subject", exam.subject).put("correct", exam.correct)
                .put("wrong", exam.wrong).put("blank", exam.blank)
                .put("createdAt", exam.createdAt).put("track", exam.track)
                .put("publisher", exam.publisher).put("examDate", exam.examDate)
                .put("mood", exam.mood)
            val parts = JSONArray()
            exam.parts.forEach { subject ->
                parts.put(JSONObject()
                    .put("key", subject.key).put("title", subject.title)
                    .put("questions", subject.questions)
                    .put("correct", subject.correct).put("wrong", subject.wrong))
            }
            record.put("parts", parts)
            arr.put(record)
        }
        return arr.toString()
    }

    fun decode(json: String): List<ExamRecord> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val record = array.getJSONObject(i)
                val rows = record.optJSONArray("parts") ?: JSONArray()
                val parts = (0 until rows.length()).map { j ->
                    val row = rows.getJSONObject(j)
                    SubjectScore(
                        row.getString("key"), row.getString("title"),
                        row.getInt("questions"), row.getInt("correct"), row.getInt("wrong"))
                }
                val exam = ExamRecord(
                    id = record.getLong("id"), name = record.optString("name"),
                    category = record.optString("category", "Genel"),
                    examType = record.optString("examType", "TYT"),
                    subject = record.optString("subject"),
                    correct = record.getInt("correct"),
                    wrong = record.getInt("wrong"),
                    blank = record.getInt("blank"),
                    createdAt = record.getLong("createdAt"),
                    track = record.optString("track", ""),
                    parts = parts,
                    publisher = record.optString("publisher", ""),
                    examDate = if (record.has("examDate") && !record.isNull("examDate"))
                        record.optLong("examDate", record.getLong("createdAt"))
                        else record.getLong("createdAt"),
                    mood = record.optString("mood", "Normal")
                )
                // A broken part table must not contaminate analyses.
                // Do not invent missing parts on legacy totals.
                if (parts.isNotEmpty() &&
                    (!ExamStructure.valid(parts, exam.examType, exam.track) ||
                        parts.sumOf { it.correct } != exam.correct ||
                        parts.sumOf { it.wrong } != exam.wrong ||
                        parts.sumOf { it.blank } != exam.blank)
                ) exam.copy(parts = emptyList())
                else exam
            }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }
}
