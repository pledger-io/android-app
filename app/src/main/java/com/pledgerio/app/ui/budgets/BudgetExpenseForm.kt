package com.pledgerio.app.ui.budgets

internal fun formatBudgetAmountInput(amount: Double): String =
    if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        amount.toString()
    }

internal fun validateExpenseForm(name: String, amountInput: String): String? {
    if (name.isBlank()) return "Enter a name for this expense group"
    val amount = amountInput.replace(',', '.').toDoubleOrNull()
    if (amount == null || amount <= 0) return "Enter a valid monthly budget amount"
    return null
}

internal fun validateIncomeForm(amountInput: String): String? {
    val amount = amountInput.replace(',', '.').toDoubleOrNull()
    if (amount == null || amount < 0) return "Enter a valid monthly net income"
    return null
}
