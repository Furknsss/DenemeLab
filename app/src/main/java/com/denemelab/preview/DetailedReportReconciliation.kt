package com.denemelab.preview

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Combines the full-page OCR with independent high-resolution reads of the
 * result table and topic table. All accepted numbers must be supported by
 * actual recognised rows and remain within the scored lesson totals.
 *
 * The publisher's right-hand headings are S / D / Y / B%:
 * S=question count, D=correct, Y=wrong, B%=success percentage (NOT blanks).
 * Blanks are derived as S-D-Y. Never interpret the final column as blanks.
 */
object DetailedReportReconciliation {
    private val finalColumns = Regex(
        """(?:^|\s)(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,3})\s*%?\s*$"""
    )

    private val partialColumns = Regex(
        """^(.{7,175}?)\\s+(\\d{1,2})\\s+(\\d{1,2})\\s+(\\d{1,2})\\s*$"""
    )

    /**
     * [leftRows] are independently recognised from the top/left table.
     * [rightRows] are independently recognised from high-resolution right
     * strips, already ordered top to bottom. Never send document metadata or
     * the student name to a remote service.
     */
    fun reconcile(raw: String, leftRows: List<String>, rightRows: List<String>): ReportResult {
        val warnings = mutableListOf<String>()
        val safeOverview = raw.lines().filter { row ->
            val columns = finalColumns.find(row.trim()) ?: return@filter true
            val values = columns.groupValues.drop(1).mapNotNull { it.toIntOrNull() }
            if (values.size != 4) return@filter false
            val (s,d,y,pct) = values
            s == 0 || (s in 1..60 && d in 0..s && y in 0..s &&
                d + y <= s && pct in 0..100 &&
                abs(pct - (100.0 * d / s).roundToInt()) <= 2)
        }.joinToString("\n")
        // Even the coarse overview must pass the percentage consistency
        // check before its topic observations become trusted evidence.
        val page = ReportTableParser.parse(safeOverview)
        val left = ReportTableParser.parse(leftRows.joinToString("\n"))
        val subjectConflict = page.counts.keys.intersect(left.counts.keys)
            .filter { page.counts[it] != left.counts[it] }.toSet()
        if (subjectConflict.isNotEmpty())
            warnings.add("Ders tablosundaki ${subjectConflict.size} satır farklı okundu; lütfen rakamları kontrol et.")

        // Cropped, high resolution table wins if two OCR passes disagree;
        // the ambiguous subject still needs an explicit student review.
        val mergedCounts = LinkedHashMap(page.counts)
        mergedCounts.putAll(left.counts)

        val validatedRight = mutableListOf<String>()
        var suspectNumericLines = 0
        var recoveredWithoutPercent = 0
        var detectedRows = 0
        for (row in rightRows) {
            val trimmed = row.trim()
            val columns = finalColumns.find(trimmed)
            if (columns == null) {
                // A tiny B% glyph may disappear while S/D/Y remain.
                // Salvage the counts, but explicitly ask the user to check.
                val partial = partialColumns.matchEntire(trimmed)
                val s = partial?.groupValues?.get(2)?.toIntOrNull()
                val d = partial?.groupValues?.get(3)?.toIntOrNull()
                val w = partial?.groupValues?.get(4)?.toIntOrNull()
                if (s != null && d != null && w != null &&
                    s in 1..40 && d in 0..s && w in 0..s && d + w <= s) {
                    val percentage = (100.0 * d / s).roundToInt()
                    validatedRight += "${partial.groupValues[1]} $s $d $w $percentage"
                    recoveredWithoutPercent++
                    detectedRows++
                } else validatedRight += trimmed
                continue
            }
            val (s,d,y,pct) = columns.groupValues.drop(1).mapNotNull { it.toIntOrNull() }
                .takeIf { it.size == 4 } ?: continue
            if (s == 0) {
                // Zero-question curriculum rows aren't errors; their
                // zero percentages are not recorded as demonstrated skill.
                continue
            }
            val expected = (100.0 * d / s).roundToInt()
            if (s !in 1..60 || d !in 0..s || y !in 0..s ||
                d + y > s || pct !in 0..100 || abs(pct-expected) > 2) {
                suspectNumericLines++
                continue
            }
            detectedRows++
            validatedRight += trimmed.removeSuffix("%")
        }
        if (suspectNumericLines > 0)
            warnings.add("$suspectNumericLines kazanım satırında sütunlar tutarsız; doğrulama gerekli.")
        if (recoveredWithoutPercent > 0)
            warnings.add("$recoveredWithoutPercent kazanımın B% sütunu okunamadı; hesaplanan yüzdeyi kontrol et.")

        // The legacy parser validates right-hand topic totals against the
        // left-hand lesson table. Supply those lines in the same pass, or a
        // right-only scan would discard every topic as ungrounded.
        val right = ReportTableParser.parse(
            (leftRows + validatedRight).joinToString("\n")
        )
        // Different recognition resolutions can recover different rows. Keep
        // the high resolution row first; deduplicate by lesson+topic, never by
        // lesson alone or a topic name can disappear.
        val candidates = (right.outcomes + page.outcomes)
            .distinctBy { TopicAnalyticsV3.identity(it.lessonKey, it.topic) }

        val accepted = mutableListOf<TopicOutcome>()
        var discarded = 0
        for ((key, rows) in candidates.groupBy { it.lessonKey }) {
            val totals = mergedCounts[key]
            val lesson = ExamStructure.subjectsForEntry("TYT", "").firstOrNull { it.key == key }
            if (totals == null || lesson == null) {
                discarded += rows.size
                continue
            }
            var usedCorrect = 0
            var usedWrong = 0
            var usedBlank = 0
            for (outcome in rows) {
                if (outcome.questions <= 0 ||
                    usedCorrect + outcome.correct > totals.first ||
                    usedWrong + outcome.wrong > totals.second ||
                    usedBlank + outcome.blank > lesson.questions - totals.first - totals.second) {
                    discarded++
                    continue
                }
                accepted.add(outcome)
                usedCorrect += outcome.correct
                usedWrong += outcome.wrong
                usedBlank += outcome.blank
            }
        }
        if (discarded > 0)
            warnings.add("$discarded kazanım satırı ders toplamıyla eşleşmedi; otomatik eklenmedi.")
        val tagged = accepted.sumOf { it.questions }
        val expectedQuestions = mergedCounts.keys.sumOf { key ->
            ExamStructure.subjectsForEntry("TYT", "").firstOrNull { it.key == key }?.questions ?: 0
        }
        if (expectedQuestions > tagged)
            warnings.add("Konu dağılımında $tagged/$expectedQuestions soru okundu; eksik satırlar olabilir.")
        if (mergedCounts.size < 10)
            warnings.add("${10 - mergedCounts.size} TYT alt dersi okunamadı; kontrol etmeden kaydetme.")
        val topics = accepted.flatMap { row ->
            listOfNotNull(
                row.wrong.takeIf { it > 0 }?.let {
                    ExamTopicIssue(row.lessonKey, row.lessonName, row.topic,
                        "Yanlış", it, "Belirtilmedi", "Belge OCR")
                },
                row.blank.takeIf { it > 0 }?.let {
                    ExamTopicIssue(row.lessonKey, row.lessonName, row.topic,
                        "Boş", it, "Belirtilmedi", "Belge OCR")
                }
            )
        }
        val title = (page.title.ifBlank { left.title }).take(90)
        return ReportResult(
            counts = mergedCounts, topics = topics, title = title,
            warnings = warnings.distinct(), rawText = raw,
            outcomes = accepted,
            detectedTopicRows = maxOf(detectedRows, candidates.size),
            validatedTopicQuestions = tagged,
            ambiguousLessons = subjectConflict
        )
    }
}
