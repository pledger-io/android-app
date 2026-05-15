package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountTypeOption
import com.pledgerio.app.domain.model.PagedAccounts
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    /** Owned (asset) accounts only — used for dashboard, sync, and the Owned filter. */
    fun getAccounts(): Flow<Resource<List<Account>>>

    /** One-shot fetch of owned accounts (safe for callers that only need a single result). */
    suspend fun refreshOwnedAccounts(): Resource<List<Account>>

    suspend fun getCounterpartyAccountsPage(
        offset: Int = 0,
        pageSize: Int = 50,
        nameQuery: String = "",
    ): Resource<PagedAccounts>
    suspend fun searchAccounts(
        typeCode: String,
        nameQuery: String,
        limit: Int = 25,
    ): Resource<List<Account>>

    suspend fun getAccountsByTypes(typeCodes: List<String>): Resource<List<Account>>
    suspend fun getAccount(id: Long): Resource<Account>
    suspend fun getAccountTypes(): Resource<List<AccountTypeOption>>
    suspend fun createAccount(account: Account): Resource<Account>
    suspend fun updateAccount(account: Account): Resource<Account>
    suspend fun deleteAccount(id: Long): Resource<Unit>
}
