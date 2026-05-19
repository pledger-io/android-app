package com.pledgerio.app.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextFormatterTest {

    @Test
    fun `formats two-column invoice rows as paired lines`() {
        val lines = listOf(
            OcrTextFormatter.OcrLine("Bread", left = 10, top = 10, right = 180, bottom = 32),
            OcrTextFormatter.OcrLine("2.50", left = 280, top = 11, right = 340, bottom = 31),
            OcrTextFormatter.OcrLine("Milk", left = 10, top = 40, right = 180, bottom = 62),
            OcrTextFormatter.OcrLine("1.20", left = 280, top = 41, right = 340, bottom = 61),
        )

        val formatted = OcrTextFormatter.format(lines)
        val rows = formatted.lines()

        assertEquals(2, rows.size)
        assertEquals("Bread\t2.50", rows[0])
        assertEquals("Milk\t1.20", rows[1])
    }

    @Test
    fun `keeps single-column OCR order when no amount column exists`() {
        val lines = listOf(
            OcrTextFormatter.OcrLine("Invoice ACME BV", left = 10, top = 10, right = 210, bottom = 32),
            OcrTextFormatter.OcrLine("Date 2026-05-18", left = 10, top = 40, right = 210, bottom = 62),
            OcrTextFormatter.OcrLine("Thank you for your purchase", left = 10, top = 70, right = 250, bottom = 92),
        )

        val formatted = OcrTextFormatter.format(lines)
        val rows = formatted.lines()

        assertEquals(3, rows.size)
        assertTrue(rows[0].contains("Invoice ACME BV"))
        assertTrue(rows[1].contains("Date 2026-05-18"))
        assertTrue(rows[2].contains("Thank you for your purchase"))
    }
}
