package com.denemelab.preview

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Optional self-reported study confirmations. No artificial automatic
 * learning statistics; next-exam deltas are only observed score changes.
 */
data class FocusCompletion(
    val id: String,
    val topicName: String,
    val lessonName: String,
    val completedAt: Long,
    val baselineExamDate: Long,
    val baselineExamNet: Double,
    val baselineExamId: Long
)

object FocusJournal {
    private const val PREFS = "denemelab_local_v1"
    private const val STORAGE_KEY = "focus_completions_v1"

    fun focusId(scope: AnalysisScope, topic: TopicPriority): String =
        "${scope.category}|${scope.examType}|${scope.detail}|${topic.key}"

    fun encode(records: List<FocusCompletion>): String = JSONArray().apply {
        records.forEach { r ->
            put(JSONObject().put("id", r.id).put("topicName", r.topicName)
                .put("lessonName", r.lessonName).put("completedAt", r.completedAt)
                .put("baselineExamDate", r.baselineExamDate)
                .put("baselineExamNet", r.baselineExamNet)
                .put("baselineExamId", r.baselineExamId))
        }
    }.toString()

    fun decode(raw: String): List<FocusCompletion> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                val id = item.getString("id")
                if (id.isBlank()) return@runCatching null
                val timestamp = item.getLong("completedAt")
                if (timestamp <= 0) return@runCatching null
                FocusCompletion(
                    id = id, topicName = item.optString("topicName"),
                    lessonName = item.optString("lessonName"),
                    completedAt = timestamp,
                    baselineExamDate = item.getLong("baselineExamDate"),
                    baselineExamNet = item.getDouble("baselineExamNet"),
                    baselineExamId = item.getLong("baselineExamId")
                )
            }.getOrNull()
        }.filterNotNull()
            .distinctBy { it.id }.sortedByDescending { it.completedAt }
    }.getOrElse { emptyList() }

    fun load(context: Context): List<FocusCompletion> = decode(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(STORAGE_KEY, "[]") ?: "[]"
    )

    fun save(context: Context, records: List<FocusCompletion>): Boolean {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(STORAGE_KEY, encode(records)).commit()
        if (saved) BackupSignal.changed(context)
        return saved
    }

    fun nextExamAfter(
        record: FocusCompletion,
        snapshot: AnalysisSnapshot
    ): ExamRecord? = snapshot.history.firstOrNull {
        it.examDate > record.completedAt && it.id != record.baselineExamId
    }

    /** Teacher-inspired but explicitly non-diagnostic suggestions. */
    fun plan(reason: String): List<String> = when (reason) {
        "Dikkat hatası" -> listOf(
            "Yanlış soruyu yeniden oku; altı çizilecek ipuçlarını işaretle.",
            "Aynı konudan 15 soru çöz; işaretleme ve işlem kontrolüne odaklan.",
            "Sonraki denemede aynı konunun yanlış/boş sayısını yeniden kaydet."
        )
        "Süre yetersizliği" -> listOf(
            "Konunun temel çözüm yolunu 10 dakika tekrar et.",
            "Benzer 15 soruyu zaman tutarak çöz; atladıklarını not et.",
            "Bir sonraki denemede hem neti hem yetişmeyen soruları kontrol et."
        )
        "İşlem hatası" -> listOf(
            "Yanlış işlemi bul ve nedenini kendi cümlenle yaz.",
            "15 soruda ara işlem ve işaret kontrolü yap.",
            "Sonraki denemede bu konuda işlem hatası tekrarını kaydet."
        )
        "Yorumlama" -> listOf(
            "Yanlış yaptığın soru kökünün ne istediğini yeniden yaz.",
            "Aynı kazanımdan 15 yorum sorusunu çöz.",
            "Bir sonraki denemede aynı kazanımdaki hata sayısını kontrol et."
        )
        else -> listOf(
            "Bu konunun temel kural ve örneklerini 15 dakika tekrar et.",
            "Aynı konudan 20 soru çöz; takıldıklarını işaretle.",
            "Sonraki denemede aynı konunun yanlış/boş sayılarını karşılaştır."
        )
    }
}
