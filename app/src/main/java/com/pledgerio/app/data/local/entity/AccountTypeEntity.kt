package com.pledgerio.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached set of account type codes returned by `GET /v2/api/account-types`. The display
 * metadata (icon, description, counterparty flag) is resolved client-side via
 * `AccountTypeCatalog`, so we only need to persist the lowercase codes.
 */
@Entity(tableName = "account_types")
data class AccountTypeEntity(
    @PrimaryKey val code: String,
)
