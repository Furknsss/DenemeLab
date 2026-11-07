package com.denemelab.preview

import kotlin.math.abs

/**
 * Extract searchable PDF table text in column order using the ORIGINAL
 * glyph positions. This avoids mixing the left-side score table with the
 * right-side curriculum table when PDFBox reads a two-column page.
 *
 * Only real PDF glyphs are used; no subject or score is inferred here.
 */
object ReportPdfPositionedRowsV22 {
    data class Glyph(
        val value: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )
    data class Columns(
        val left: List<String>,
        val right: List<String>
    )

    fun columns(glyphs: List<Glyph>, pageWidth: Float): Columns {
        if (pageWidth <= 0f || glyphs.isEmpty()) return Columns(emptyList(), emptyList())
        val clean = glyphs.filter { it.value.isNotEmpty() &&
            it.x >= 0 && it.y >= 0 &&
            it.x.isFinite() && it.y.isFinite() &&
            it.width >= 0 && it.height >= 0
        }
        val midpoint = pageWidth * .5f
        return Columns(
            lines(clean.filter { it.x < midpoint }),
            lines(clean.filter { it.x >= midpoint })
        )
    }

    private fun lines(glyphs: List<Glyph>): List<String> {
        if (glyphs.isEmpty()) return emptyList()
        val heights = glyphs.map { it.height }.filter { it > 0 }.sorted()
        val typicalHeight = heights.getOrElse(heights.size / 2) { 8f }
        val yTolerance = (typicalHeight * .30f).coerceIn(1f,3.2f)
        val rows = mutableListOf<MutableList<Glyph>>()
        glyphs.sortedWith(compareBy<Glyph> { it.y }.thenBy { it.x }).forEach { g ->
            val row = rows.lastOrNull()
            if (row != null && abs(row.map { it.y }.average() - g.y) <= yTolerance)
                row.add(g) else rows.add(mutableListOf(g))
        }
        return rows.mapNotNull { row ->
            val ordered = row.sortedBy { it.x }
            val sb = StringBuilder()
            var right = 0f
            var previousWidth = 0f
            for (glyph in ordered) {
                val fragment = glyph.value.replace('\n',' ').trim()
                if (fragment.isBlank()) continue
                val gap = glyph.x - right
                if (sb.isNotEmpty() && gap > (previousWidth * .35f).coerceAtLeast(1.3f))
                    sb.append(' ')
                sb.append(fragment)
                right = (glyph.x + glyph.width).coerceAtLeast(glyph.x)
                previousWidth = if (glyph.width > 0) glyph.width /
                    fragment.length.coerceAtLeast(1) else 3f
            }
            sb.toString().trim().takeIf { it.isNotEmpty() }
        }
    }
}
