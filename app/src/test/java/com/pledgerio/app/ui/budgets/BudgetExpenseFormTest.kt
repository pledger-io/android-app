package com.pledgerio.app.ui.budgets

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetExpenseFormTest {

    @Test
    fun `expense amount must be positive and finite`() {
        listOf("Infinity", "NaN", "0", "-1").forEach { amount ->
            assertNotNull("$amount must be rejected", validateExpenseForm("Rent", amount))
        }
        assertNull(validateExpenseForm("Rent", "100"))
    }

    @Test
    fun `income amount must be non-negative and finite`() {
        listOf("Infinity", "NaN", "-1").forEach { amount ->
            assertNotNull("$amount must be rejected", validateIncomeForm(amount))
        }
        assertNull(validateIncomeForm("0"))
    }
}
