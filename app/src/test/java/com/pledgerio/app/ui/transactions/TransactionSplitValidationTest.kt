package com.pledgerio.app.ui.transactions

import com.pledgerio.app.ui.transactions.form.SplitValidationIssue
import com.pledgerio.app.ui.transactions.form.splitValidationIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `matching split totals pass production validation`() {
        val lines = listOf(
            TransactionSplitLineUi("1", "A", "30"),
            TransactionSplitLineUi("2", "B", "20"),
        )
        val state = TransactionFormUiState(amount = "50", splitLines = lines)

        assertNull(state.splitValidationIssue())
    }

    @Test
    fun `mismatched split totals fail production validation`() {
        val state = TransactionFormUiState(
            amount = "51",
            splitLines = listOf(
                TransactionSplitLineUi("1", "A", "30"),
                TransactionSplitLineUi("2", "B", "20"),
            ),
        )

        assertEquals(SplitValidationIssue.TOTAL_MISMATCH, state.splitValidationIssue())
    }

    @Test
    fun `non-finite split amount fails production validation`() {
        val state = TransactionFormUiState(
            amount = "50",
            splitLines = listOf(TransactionSplitLineUi("1", "A", "Infinity")),
        )

        assertEquals(SplitValidationIssue.LINE_AMOUNT, state.splitValidationIssue())
    }
}
