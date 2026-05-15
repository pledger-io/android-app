package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountTypeOption
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAccounts(): Flow<Resource<List<Account>>>
    suspend fun getAccount(id: Long): Resource<Account>
    suspend fun getAccountTypes(): Resource<List<AccountTypeOption>>
    suspend fun createAccount(account: Account): Resource<Account>
    suspend fun updateAccount(account: Account): Resource<Account>
    suspend fun deleteAccount(id: Long): Resource<Unit>
}
