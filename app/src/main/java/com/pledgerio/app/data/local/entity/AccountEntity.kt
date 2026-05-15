package com.pledgerio.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pledgerio.app.domain.model.Account

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val description: String = "",
    val currency: String = "EUR",
    val balance: Double = 0.0,
    val type: String = "default",
    val iconFileCode: String? = null,
    val iban: String? = null,
    val bic: String? = null,
    val openingBalance: Double = 0.0,
    val lastActivity: String? = null,
    val lastSynced: Long = System.currentTimeMillis(),
) {
    fun toDomain(): Account = Account(
        id = id,
        name = name,
        description = description,
        currency = currency,
        balance = balance,
        typeCode = type,
        iconFileCode = iconFileCode,
        iban = iban,
        bic = bic,
        openingBalance = openingBalance,
        lastActivity = lastActivity,
    )

    companion object {
        fun fromDomain(account: Account): AccountEntity = AccountEntity(
            id = account.id,
            name = account.name,
            description = account.description,
            currency = account.currency,
            balance = account.balance,
            type = account.typeCode,
            iconFileCode = account.iconFileCode,
            iban = account.iban,
            bic = account.bic,
            openingBalance = account.openingBalance,
            lastActivity = account.lastActivity,
        )
    }
}
