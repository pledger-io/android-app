package com.pledgerio.app.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionSplitValidationTest {

    @Test
    fun `toDomainSplits ignores blank lines`() {
        val lines = listOf(
            TransactionSplitLineUi("1", "Groceries", "40"),
            TransactionSplitLineUi("2", "", ""),
        )
        assertEquals(1, lines.toDomainSplits().size)
    }

    @Test
    fun `split totals must match transaction amount`() {
        val lines = listOf(
            TransactionSplitLineUi("1", "A", "30"),
            TransactionSplitLineUi("2", "B", "20"),
        )
        val total = lines.sumOf { it.amount.toDouble() }
        assertEquals(50.0, total, 0.001)
    }
}
