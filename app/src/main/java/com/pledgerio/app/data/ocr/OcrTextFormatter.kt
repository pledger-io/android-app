package com.pledgerio.app.data.ocr

import kotlin.math.abs
import kotlin.math.max

/**
 * Rebuilds OCR lines into a transaction-extraction friendly text stream.
 *
 * Invoices often contain a left description column and a right amount column.
 * When OCR returns those as separate reading-order blocks, this formatter pairs
 * rows by Y-position and emits tab-separated "description<TAB>amount" rows.
 */
object OcrTextFormatter {

    data class OcrLine(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val cleanText: String = text.trim()
        val centerX: Float = (left + right) / 2f
        val centerY: Float = (top + bottom) / 2f
        val height: Int = max(1, bottom - top)
    }

    private val amountLikeRegex = Regex(
        pattern = """^[\p{Sc}]?\s*[-+]?\d{1,3}(?:[.,\s]\d{3})*(?:[.,]\d{2})?(?:\s?[A-Z]{3})?$|^[\p{Sc}]?\s*[-+]?\d+(?:[.,]\d{2})?(?:\s?[A-Z]{3})?$""",
    )

    fun format(lines: List<OcrLine>): String {
        val cleaned = lines.mapNotNull { line ->
            if (line.cleanText.isBlank()) null else line.copy(text = line.cleanText)
        }
        if (cleaned.isEmpty()) return ""

        val twoColumn = tryFormatTwoColumnRows(cleaned)
        if (twoColumn != null) {
            return twoColumn.joinToString("\n")
        }
        return formatByRows(cleaned).joinToString("\n")
    }

    private fun tryFormatTwoColumnRows(lines: List<OcrLine>): List<String>? {
        if (lines.size < 4) return null
        val sortedByX = lines.sortedBy { it.centerX }
        val minLeft = sortedByX.minOf { it.left }
        val maxRight = sortedByX.maxOf { it.right }
        val totalWidth = max(1, maxRight - minLeft)

        var maxGap = 0f
        var splitIndex = -1
        for (index in 0 until sortedByX.lastIndex) {
            val gap = sortedByX[index + 1].centerX - sortedByX[index].centerX
            if (gap > maxGap) {
                maxGap = gap
                splitIndex = index
            }
        }

        if (splitIndex < 0 || maxGap < totalWidth * 0.22f) return null

        val splitX = (sortedByX[splitIndex].centerX + sortedByX[splitIndex + 1].centerX) / 2f
        val leftColumn = lines.filter { it.centerX < splitX }.sortedBy { it.centerY }
        val rightColumn = lines.filter { it.centerX >= splitX }.sortedBy { it.centerY }

        if (leftColumn.isEmpty() || rightColumn.isEmpty()) return null

        val rightAmountRatio = rightColumn.count { isAmountLike(it.cleanText) }.toFloat() / rightColumn.size
        val leftAmountRatio = leftColumn.count { isAmountLike(it.cleanText) }.toFloat() / leftColumn.size
        if (rightAmountRatio < 0.6f || leftAmountRatio > 0.45f) return null

        val medianHeight = medianHeight(lines)
        val yThreshold = max(8f, medianHeight * 0.95f)
        val usedRightIndices = mutableSetOf<Int>()
        val merged = mutableListOf<Pair<Float, String>>()

        leftColumn.forEach { left ->
            val candidate = rightColumn
                .withIndex()
                .filter { (index, line) ->
                    index !in usedRightIndices &&
                        abs(line.centerY - left.centerY) <= yThreshold
                }
                .minByOrNull { (_, line) -> abs(line.centerY - left.centerY) }

            if (candidate != null) {
                usedRightIndices += candidate.index
                merged += ((left.centerY + candidate.value.centerY) / 2f) to
                    "${left.cleanText}\t${candidate.value.cleanText}"
            } else {
                merged += left.centerY to left.cleanText
            }
        }

        rightColumn.withIndex()
            .filter { (index, _) -> index !in usedRightIndices }
            .forEach { (_, line) ->
                merged += line.centerY to line.cleanText
            }

        return merged
            .sortedBy { it.first }
            .map { it.second }
    }

    private fun formatByRows(lines: List<OcrLine>): List<String> {
        val sorted = lines.sortedWith(compareBy<OcrLine> { it.centerY }.thenBy { it.left })
        val threshold = max(8f, medianHeight(lines) * 0.65f)
        val rows = mutableListOf<MutableList<OcrLine>>()

        sorted.forEach { line ->
            val current = rows.lastOrNull()
            if (current == null) {
                rows += mutableListOf(line)
                return@forEach
            }
            val rowCenterY = current.map { it.centerY }.average().toFloat()
            if (abs(line.centerY - rowCenterY) <= threshold) {
                current += line
            } else {
                rows += mutableListOf(line)
            }
        }

        return rows.map { row ->
            row.sortedBy { it.left }
                .joinToString(separator = "\t") { it.cleanText }
        }
    }

    private fun medianHeight(lines: List<OcrLine>): Float {
        val sorted = lines.map { it.height }.sorted()
        if (sorted.isEmpty()) return 12f
        return if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2].toFloat()
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
        }
    }

    private fun isAmountLike(text: String): Boolean = amountLikeRegex.matches(text.trim())
}
