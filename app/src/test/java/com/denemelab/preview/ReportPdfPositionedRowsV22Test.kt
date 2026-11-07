package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class ReportPdfPositionedRowsV22Test {
    private fun word(value: String, x: Float, y: Float): List<ReportPdfPositionedRowsV22.Glyph> {
        return value.mapIndexed { index, ch ->
            ReportPdfPositionedRowsV22.Glyph(
                ch.toString(), x + index * 5.5f, y, 5.5f, 8f)
        }
    }

    @Test fun originalLeftAndRightColumnsRemainSeparateEvenWhenRowsOverlapVertically() {
        val glyphs = word("Turkce",30f,80f) + word("40",120f,80f) +
            word("28",150f,80f) + word("11",180f,80f) +
            word("Konu",330f,80f) + word("1",420f,80f) +
            word("1",440f,80f) + word("0",460f,80f) +
            word("100",480f,80f)
        val result = ReportPdfPositionedRowsV22.columns(glyphs,595f)
        assertEquals(1,result.left.size)
        assertEquals(1,result.right.size)
        assertTrue(result.left.single().contains("40 28 11"))
        assertTrue(result.right.single().contains("Konu 1 1 0 100"))
    }

    @Test fun adjacentSmallPrintLinesMustNotMerge() {
        val glyphs = word("TopikA",330f,80f) + word("1",430f,80f) +
            word("TopikB",330f,88f) + word("2",430f,88f)
        val result = ReportPdfPositionedRowsV22.columns(glyphs,595f)
        assertEquals(2,result.right.size)
        assertTrue(result.right[0].contains("TopikA"))
        assertTrue(result.right[1].contains("TopikB"))
    }

    @Test fun blankOrInvalidCoordinatesAreNotInterpretedAsFacts() {
        assertTrue(ReportPdfPositionedRowsV22.columns(emptyList(),595f).right.isEmpty())
    }
}
