package com.pledgerio.app.ui.transactions.form

import com.pledgerio.app.domain.model.TransactionType

object TransactionFormLabels {

    fun typeSubtitle(type: TransactionType): String = when (type) {
        TransactionType.DEBIT -> "Record money you received"
        TransactionType.CREDIT -> "Record money you spent"
        TransactionType.TRANSFER -> "Move money between your accounts"
    }

    fun sourceLabel(type: TransactionType): String = when (type) {
        TransactionType.DEBIT -> "Received from"
        TransactionType.CREDIT -> "Paid from"
        TransactionType.TRANSFER -> "From"
    }

    fun targetLabel(type: TransactionType): String = when (type) {
        TransactionType.DEBIT -> "Deposited to"
        TransactionType.CREDIT -> "To (payee)"
        TransactionType.TRANSFER -> "To"
    }

    fun flowHelperText(type: TransactionType): String = when (type) {
        TransactionType.DEBIT -> "Money is recorded from a party into one of your accounts."
        TransactionType.CREDIT -> "Money leaves your account and is recorded against a payee."
        TransactionType.TRANSFER -> "Money moves between two accounts you own."
    }
}
