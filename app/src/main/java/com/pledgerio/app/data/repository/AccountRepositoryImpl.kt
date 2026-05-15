package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.AccountDao
import com.pledgerio.app.data.local.entity.AccountEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.BalanceRequest
import com.pledgerio.app.data.remote.dto.CreateAccountRequest
import com.pledgerio.app.data.remote.dto.DateRangeDto
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountTypeCatalog
import com.pledgerio.app.domain.model.AccountTypeCodes
import com.pledgerio.app.domain.model.AccountTypeOption
import com.pledgerio.app.domain.model.PagedAccounts
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val accountDao: AccountDao,
) : AccountRepository {

    override fun getAccounts(): Flow<Resource<List<Account>>> = flow {
        emit(Resource.Loading)
        emit(refreshOwnedAccounts())
    }

    override suspend fun refreshOwnedAccounts(): Resource<List<Account>> {
        return try {
            val accounts = fetchOwnedAccountsFromApi()
            if (accounts != null) {
                val enriched = enrichWithBalances(accounts)
                accountDao.deleteAll()
                accountDao.insertAll(enriched.map { AccountEntity.fromDomain(it) })
                Resource.Success(enriched)
            } else {
                cachedOwnedOrError("Failed to fetch accounts")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cachedOwnedOrError(e.message ?: "Network error")
        }
    }

    override suspend fun getCounterpartyAccountsPage(
        offset: Int,
        pageSize: Int,
        nameQuery: String,
    ): Resource<PagedAccounts> {
        return try {
            val response = apiService.getAccounts(
                type = AccountTypeCodes.counterpartyTypeCodes.toList(),
                accountName = nameQuery.trim().ifBlank { null },
                offset = offset,
                numberOfResults = pageSize,
            )
            if (response.isSuccessful) {
                val body = response.body()
                val items = body?.content
                    ?.map { it.toDomain() }
                    ?.distinctBy { it.id }
                    ?: emptyList()
                val total = body?.info?.records ?: items.size.toLong()
                Resource.Success(
                    PagedAccounts(
                        items = items,
                        totalRecords = total,
                        offset = offset,
                        pageSize = pageSize,
                    ),
                )
            } else {
                Resource.Error("Failed to fetch counterparties: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    /**
     * Owned asset accounts only. Creditor/debtor accounts are loaded on demand via
     * [getCounterpartyAccountsPage] so large counterparty lists stay paginated.
     */
    private suspend fun fetchOwnedAccountsFromApi(): List<Account>? {
        val ownedTypes = resolveOwnedTypeCodes()
        val dtos = when {
            ownedTypes.isNotEmpty() -> fetchAccountDtos(ownedTypes)
            else -> fetchAccountDtos(types = null)
        } ?: return null

        return dtos
            .map { it.toDomain() }
            .distinctBy { it.id }
            .filter { !AccountTypeCatalog.isCounterparty(it.typeCode) }
    }

    private suspend fun resolveOwnedTypeCodes(): List<String> {
        return try {
            val response = apiService.getAccountTypes()
            if (response.isSuccessful) {
                response.body().orEmpty()
                    .map { it.lowercase() }
                    .filter { it !in AccountTypeCodes.counterpartyTypeCodes }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchAccountDtos(types: List<String>?): List<com.pledgerio.app.data.remote.dto.AccountDto>? {
        val response = apiService.getAccounts(
            type = types?.takeIf { it.isNotEmpty() },
            offset = 0,
            numberOfResults = 200,
        )
        if (!response.isSuccessful) return null
        return response.body()?.content ?: emptyList()
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

    private suspend fun cachedOwnedOrError(message: String): Resource<List<Account>> {
        val cached = accountDao.getAll().first()
        return if (cached.isNotEmpty()) {
            val owned = cached.map { it.toDomain() }
                .distinctBy { it.id }
                .filter { !AccountTypeCatalog.isCounterparty(it.typeCode) }
            Resource.Success(owned)
        } else {
            Resource.Error(message)
        }
    }

    override suspend fun searchAccounts(
        typeCode: String,
        nameQuery: String,
        limit: Int,
    ): Resource<List<Account>> {
        return try {
            val response = apiService.getAccounts(
                type = listOf(typeCode),
                accountName = nameQuery.ifBlank { null },
                offset = 0,
                numberOfResults = limit,
            )
            if (response.isSuccessful) {
                val accounts = response.body()?.content
                    ?.map { it.toDomain() }
                    ?.distinctBy { it.id }
                    ?: emptyList()
                Resource.Success(accounts)
            } else {
                Resource.Error("Failed to search accounts: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun getAccountsByTypes(typeCodes: List<String>): Resource<List<Account>> {
        if (typeCodes.isEmpty()) return Resource.Success(emptyList())
        return try {
            val response = apiService.getAccounts(
                type = typeCodes,
                offset = 0,
                numberOfResults = 200,
            )
            if (response.isSuccessful) {
                val accounts = response.body()?.content
                    ?.map { it.toDomain() }
                    ?.distinctBy { it.id }
                    ?: emptyList()
                Resource.Success(accounts.sortedBy { it.name })
            } else {
                Resource.Error("Failed to fetch accounts: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun getAccount(id: Long): Resource<Account> {
        return try {
            val response = apiService.getAccount(id)
            if (response.isSuccessful) {
                val dto = response.body() ?: return Resource.Error("Account not found")
                val account = dto.toDomain()
                val enriched = if (AccountTypeCatalog.isCounterparty(account.typeCode)) {
                    account
                } else {
                    enrichWithBalances(listOf(account)).first()
                }
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
                val ownedCodes = response.body().orEmpty()
                    .filter { it.lowercase() !in AccountTypeCodes.counterpartyTypeCodes }
                Resource.Success(AccountTypeCatalog.toOptions(ownedCodes))
            } else {
                Resource.Success(AccountTypeCatalog.toOptions(emptyList()))
            }
        } catch (_: Exception) {
            Resource.Success(AccountTypeCatalog.toOptions(emptyList()))
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
                if (!AccountTypeCatalog.isCounterparty(created.typeCode)) {
                    accountDao.insert(AccountEntity.fromDomain(created))
                }
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
                if (!AccountTypeCatalog.isCounterparty(account.typeCode)) {
                    accountDao.insert(AccountEntity.fromDomain(account))
                }
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
            iconFileCode = iconFileCode,
            iban = account?.iban,
            bic = account?.bic,
            lastActivity = history?.lastTransaction,
        )
    }
}
