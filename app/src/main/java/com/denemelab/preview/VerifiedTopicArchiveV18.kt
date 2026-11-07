package com.denemelab.preview

import android.content.Context
import org.json.JSONObject

/**
 * Only audited five-year topic counts are allowed. OSYM publishes question
 * booklets but NOT an official topic-by-topic count for every subject.
 * Third-party or inferred counts must not silently be branded as official.
 *
 * Optional asset: assets/verified_topic_distribution.json
 * { "source": "DenemeLab verified question classification",
 *   "lessons": { "tyt_mat": { "Problemler": { "2024": 12 } } } }
 * Source citations belong in a separate verified dataset before release.
 */
object VerifiedTopicArchiveV18 {
    fun counts(context: Context, lessonKey: String): Map<String, Map<Int, Int>> {
        val root = runCatching {
            context.assets.open("verified_topic_distribution.json")
                .bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull() ?: return emptyMap()
        // Fail closed when provenance metadata is absent.
        if (root.optString("source") != "DenemeLab verified question classification" ||
            root.optJSONArray("references")?.length() ?: 0 < 1) return emptyMap()
        val node = root.optJSONObject("lessons")?.optJSONObject(lessonKey) ?: return emptyMap()
        return node.keys().asSequence().mapNotNull { topic ->
            val years = node.optJSONObject(topic) ?: return@mapNotNull null
            val verified = years.keys().asSequence().mapNotNull { key ->
                val year = key.toIntOrNull()
                val amount = if (year != null) years.optInt(key, -1) else -1
                if (year != null && year in 2018..2100 && amount >= 0 && amount <= 40)
                    year to amount else null
            }.toMap()
            if (verified.isEmpty()) null else topic to verified
        }.toMap()
    }
}
