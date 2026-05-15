package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.AccountDao
import com.pledgerio.app.data.local.entity.AccountEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.BalanceRequest
import com.pledgerio.app.data.remote.dto.CreateAccountRequest
import com.pledgerio.app.data.remote.dto.DateRangeDto
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountTypeCodes
import com.pledgerio.app.domain.model.AccountTypeOption
import com.pledgerio.app.domain.model.toAccountTypeDisplayName
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val accountDao: AccountDao,
) : AccountRepository {

    override fun getAccounts(): Flow<Resource<List<Account>>> = flow {
        emit(Resource.Loading)

        try {
            val response = apiService.getAccounts(offset = 0, numberOfResults = 100)
            if (response.isSuccessful) {
                val accounts = response.body()?.content?.map { it.toDomain() } ?: emptyList()

                val enriched = enrichWithBalances(accounts)

                accountDao.deleteAll()
                accountDao.insertAll(enriched.map { AccountEntity.fromDomain(it) })
                emit(Resource.Success(enriched))
            } else {
                emitCachedOrError("Failed to fetch accounts: ${response.code()}")
            }
        } catch (e: Exception) {
            emitCachedOrError(e.message ?: "Network error")
        }
    }

    private suspend fun enrichWithBalances(accounts: List<Account>): List<Account> {
        if (accounts.isEmpty()) return accounts

        return try {
            val balanceRequest = BalanceRequest(
                range = DateRangeDto(
                    startDate = "1970-01-01",
                    endDate = LocalDate.now().plusDays(1).toString(),
                ),
                accounts = accounts.map { it.id },
            )
            val balanceResponse = apiService.getPartitionedBalance("account", balanceRequest)
            if (balanceResponse.isSuccessful) {
                val balancesByPartition = balanceResponse.body()
                    ?.associateBy({ it.partition }, { it.balance })
                    ?: emptyMap()

                accounts.map { account ->
                    val balance = balancesByPartition[account.name] ?: 0.0
                    account.copy(balance = balance)
                }
            } else {
                accounts
            }
        } catch (_: Exception) {
            accounts
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<Resource<List<Account>>>.emitCachedOrError(message: String) {
        accountDao.getAll().collect { cached ->
            if (cached.isNotEmpty()) {
                emit(Resource.Success(cached.map { it.toDomain() }))
            } else {
                emit(Resource.Error(message))
            }
        }
    }

    override suspend fun getAccount(id: Long): Resource<Account> {
        return try {
            val response = apiService.getAccount(id)
            if (response.isSuccessful) {
                val dto = response.body() ?: return Resource.Error("Account not found")
                val account = dto.toDomain()
                val enriched = enrichWithBalances(listOf(account)).first()
                Resource.Success(enriched)
            } else {
                val cached = accountDao.getById(id)
                if (cached != null) Resource.Success(cached.toDomain())
                else Resource.Error("Failed to fetch account")
            }
        } catch (e: Exception) {
            val cached = accountDao.getById(id)
            if (cached != null) Resource.Success(cached.toDomain())
            else Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun getAccountTypes(): Resource<List<AccountTypeOption>> {
        return try {
            val response = apiService.getAccountTypes()
            if (response.isSuccessful) {
                val counterpartyCodes = setOf("creditor", "debtor", "debit")
                val owned = response.body().orEmpty()
                    .filter { it.lowercase() !in counterpartyCodes }
                    .map { code ->
                        AccountTypeOption(code = code, displayName = code.toAccountTypeDisplayName())
                    }
                Resource.Success(owned + AccountTypeCodes.counterpartyTypes)
            } else {
                Resource.Success(AccountTypeCodes.counterpartyTypes)
            }
        } catch (_: Exception) {
            Resource.Success(AccountTypeCodes.counterpartyTypes)
        }
    }

    override suspend fun createAccount(account: Account): Resource<Account> {
        return try {
            val request = CreateAccountRequest(
                name = account.name,
                description = account.description,
                currency = account.currency,
                type = account.typeCode,
                iban = account.iban,
                bic = account.bic,
            )
            val response = apiService.createAccount(request)
            if (response.isSuccessful) {
                val created = response.body()?.toDomain() ?: return Resource.Error("Invalid response")
                accountDao.insert(AccountEntity.fromDomain(created))
                Resource.Success(created)
            } else {
                Resource.Error("Failed to create account: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun updateAccount(account: Account): Resource<Account> {
        return try {
            val request = CreateAccountRequest(
                name = account.name,
                description = account.description,
                currency = account.currency,
                type = account.typeCode,
                iban = account.iban,
                bic = account.bic,
            )
            val response = apiService.updateAccount(account.id, request)
            if (response.isSuccessful) {
                accountDao.insert(AccountEntity.fromDomain(account))
                Resource.Success(account)
            } else {
                Resource.Error("Failed to update account: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun deleteAccount(id: Long): Resource<Unit> {
        return try {
            val response = apiService.deleteAccount(id)
            if (response.isSuccessful) {
                accountDao.getById(id)?.let { accountDao.delete(it) }
                Resource.Success(Unit)
            } else {
                Resource.Error("Failed to delete account: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    private fun com.pledgerio.app.data.remote.dto.AccountDto.toDomain(): Account {
        return Account(
            id = id,
            name = name,
            description = description ?: "",
            currency = account?.currency ?: "EUR",
            typeCode = type,
            iban = account?.iban,
            bic = account?.bic,
            lastActivity = history?.lastTransaction,
        )
    }
}
