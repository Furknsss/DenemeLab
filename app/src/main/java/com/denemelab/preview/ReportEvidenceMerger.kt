package com.denemelab.preview

/**
 * Reconcile separate evidence sources (native PDF text vs image OCR).
 * Combine non-conflicting courses and topic observations. A disagreement
 * MUST be returned to the user as ambiguous instead of inventing certainty.
 */
object ReportEvidenceMerger {
    fun merge(raster: ReportResult, native: ReportResult): ReportResult {
        val subjects = ExamStructure.tytDetailed.associateBy { it.key }
        val counts = LinkedHashMap(raster.counts)
        val conflicted = mutableSetOf<String>()
        for ((key, value) in native.counts) {
            val old = counts[key]
            when {
                old == null -> counts[key] = value
                old != value -> conflicted.add(key)
            }
        }
        conflicted.addAll(raster.ambiguousLessons)
        conflicted.addAll(native.ambiguousLessons)
        val topicsByIdentity = (raster.outcomes + native.outcomes)
            .groupBy { TopicAnalyticsV3.identity(it.lessonKey, it.topic) }
        val discordant = topicsByIdentity.filter { (_, variants) ->
            variants.map { Triple(it.correct, it.wrong, it.blank) }.distinct().size > 1
        }.keys
        val candidates = topicsByIdentity
            .filterKeys { it !in discordant }
            .values.map { it.first() }
        val accepted = mutableListOf<TopicOutcome>()
        for ((key, courses) in candidates.groupBy { it.lessonKey }) {
            val pair = counts[key] ?: continue
            val template = subjects[key] ?: continue
            val blank = template.questions - pair.first - pair.second
            if (blank < 0) continue
            var c = 0
            var w = 0
            var b = 0
            for (row in courses) {
                if (row.questions <= 0 || c + row.correct > pair.first ||
                    w + row.wrong > pair.second || b + row.blank > blank) continue
                accepted.add(row)
                c += row.correct
                w += row.wrong
                b += row.blank
            }
        }
        val topics = accepted.flatMap { row ->
            listOfNotNull(
                row.wrong.takeIf { it > 0 }?.let {
                    ExamTopicIssue(row.lessonKey,row.lessonName,row.topic,"Yanlış",it,
                        "Belirtilmedi","Belge OCR")
                },
                row.blank.takeIf { it > 0 }?.let {
                    ExamTopicIssue(row.lessonKey,row.lessonName,row.topic,"Boş",it,
                        "Belirtilmedi","Belge OCR")
                }
            )
        }
        val warnings = mutableListOf<String>()
        warnings.addAll(raster.warnings)
        warnings.addAll(native.warnings)
        if (conflicted.isNotEmpty())
            warnings.add("${conflicted.size} derste PDF metni ile görsel okuma çelişiyor; dersleri doğrula.")
        if (discordant.isNotEmpty())
            warnings.add("${discordant.size} konuda iki okuma uyuşmadı; konular otomatik kabul edilmedi.")
        return ReportResult(
            counts = counts,
            topics = topics,
            title = raster.title.ifBlank { native.title },
            warnings = warnings.distinct(),
            rawText = raster.rawText + "\n" + native.rawText,
            outcomes = accepted,
            detectedTopicRows = topicsByIdentity.size,
            validatedTopicQuestions = accepted.sumOf { it.questions },
            ambiguousLessons = conflicted
        )
    }
}
