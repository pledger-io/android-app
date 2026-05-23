package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CachePolicy
import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.cache.SyncKeys
import com.pledgerio.app.data.local.dao.AccountDao
import com.pledgerio.app.data.local.dao.AccountTypeDao
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val accountDao: AccountDao,
    private val accountTypeDao: AccountTypeDao,
    private val cacheRefresher: CacheRefresher,
) : AccountRepository {

    private val counterpartyTypes = AccountTypeCodes.counterpartyTypeCodes.toList()

    override fun getAccounts(): Flow<Resource<List<Account>>> = flow {
        emit(Resource.Loading)
        val cached = ownedFromCache()
        // Always emit a terminal Success (even if empty) so collectors leave the
        // Loading state. Without this, an empty-but-fresh cache (e.g. after a
        // destructive migration or a server with no owned accounts) would leave the
        // UI spinning forever because the refresh branch below would be skipped.
        emit(Resource.Success(cached))
        if (cacheRefresher.isStale(SyncKeys.OWNED_ACCOUNTS, CachePolicy.OWNED_ACCOUNTS_TTL_MS)) {
            val refreshed = refreshOwnedAccounts()
            when (refreshed) {
                is Resource.Success -> emit(refreshed)
                is Resource.Error -> if (cached.isEmpty()) emit(refreshed)
                is Resource.Loading -> Unit
            }
        }
    }

    override fun observeOwnedAccounts(): Flow<List<Account>> =
        accountDao.getAll()
            .map { rows ->
                rows.map { it.toDomain() }
                    .filter { !AccountTypeCatalog.isCounterparty(it.typeCode) }
            }
            .distinctUntilChanged()
            .onStart {
                cacheRefresher.launchIfStale(
                    key = SyncKeys.OWNED_ACCOUNTS,
                    ttlMs = CachePolicy.OWNED_ACCOUNTS_TTL_MS,
                ) { refreshOwnedAccounts() }
            }

    override suspend fun refreshOwnedAccounts(): Resource<List<Account>> {
        return cacheRefresher.refreshNow(SyncKeys.OWNED_ACCOUNTS) {
            try {
                val accounts = fetchOwnedAccountsFromApi()
                if (accounts != null) {
                    val enriched = enrichWithBalances(accounts)
                    accountDao.replaceByTypes(
                        types = ownedTypesForCacheReplace(enriched),
                        items = enriched.map { AccountEntity.fromDomain(it) },
                    )
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
    }

    override suspend fun getCounterpartyAccountsPage(
        offset: Int,
        pageSize: Int,
        nameQuery: String,
    ): Resource<PagedAccounts> {
        // Cache-first when the local store actually holds the requested slice. A single row
        // from an earlier total probe must not short-circuit paging (see fetchCounterpartyPageFromApi).
        val trimmedQuery = nameQuery.trim()
        val cachedTotal = accountDao.countByTypes(counterpartyTypes, trimmedQuery)
        val cachedPage = accountDao.searchByTypes(
            types = counterpartyTypes,
            query = trimmedQuery,
            offset = offset,
            limit = pageSize,
        ).map { it.toDomain() }

        cacheRefresher.launchIfStale(
            key = SyncKeys.COUNTERPARTY_ACCOUNTS,
            ttlMs = CachePolicy.COUNTERPARTY_ACCOUNTS_TTL_MS,
        ) { refreshCounterpartyAccounts() }

        if (cachedPage.isNotEmpty() && cacheCoversPage(cachedPage.size, cachedTotal, offset, pageSize)) {
            val enrichedPage = enrichWithBalances(cachedPage)
            return Resource.Success(
                PagedAccounts(
                    items = enrichedPage,
                    totalRecords = cachedTotal,
                    offset = offset,
                    pageSize = pageSize,
                ),
            )
        }

        // Incomplete or empty cache — fetch this page from the API.
        return fetchCounterpartyPageFromApi(offset, pageSize, trimmedQuery)
    }

    override suspend fun refreshCounterpartyAccounts(): Resource<List<Account>> {
        return cacheRefresher.refreshNow(SyncKeys.COUNTERPARTY_ACCOUNTS) {
            try {
                val collected = mutableListOf<Account>()
                var offset = 0
                val pageSize = 200
                while (true) {
                    val response = apiService.getAccounts(
                        type = counterpartyTypes,
                        offset = offset,
                        numberOfResults = pageSize,
                    )
                    if (!response.isSuccessful) {
                        return@refreshNow Resource.Error("Failed to fetch counterparties: ${response.code()}")
                    }
                    val body = response.body()
                    val items = body?.content?.map { it.toDomain() }?.distinctBy { it.id } ?: emptyList()
                    collected.addAll(items)
                    val totalRecords = body?.info?.records ?: 0L
                    if (items.isEmpty()) break
                    if (totalRecords > 0 && collected.size.toLong() >= totalRecords) break
                    if (totalRecords == 0L && items.size < pageSize) break
                    offset = collected.size
                }
                val enriched = enrichWithBalances(collected)
                accountDao.replaceByTypes(
                    types = counterpartyTypes,
                    items = enriched.map { AccountEntity.fromDomain(it) },
                )
                Resource.Success(enriched)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Network error")
            }
        }
    }

    /**
     * Owned asset accounts only. Creditor/debtor accounts are loaded on demand via
     * [getCounterpartyAccountsPage] so large counterparty lists stay paginated.
     */
    private suspend fun fetchOwnedAccountsFromApi(): List<Account>? {
        val ownedTypes = resolveOwnedTypeCodes()
        val dtos = when {
            ownedTypes.isNotEmpty() -> fetchAccountDtosPaged(types = ownedTypes)
            else -> fetchAccountDtosPaged(types = null)
        } ?: return null

        return dtos
            .map { it.toDomain() }
            .distinctBy { it.id }
            .filter { !AccountTypeCatalog.isCounterparty(it.typeCode) }
    }

    private suspend fun resolveOwnedTypeCodes(): List<String> {
        // Cache-first: returns instantly from Room, refreshes in the background if stale.
        val cached = accountTypeDao.getAllCodes()
        cacheRefresher.launchIfStale(
            key = SyncKeys.ACCOUNT_TYPES,
            ttlMs = CachePolicy.ACCOUNT_TYPES_TTL_MS,
        ) { refreshAccountTypes() }
        val fromApi = if (cached.isNotEmpty()) {
            cached
        } else {
            // First-run or cache empty: do a synchronous fetch so refreshOwnedAccounts() has a
            // type list to query against.
            when (val refreshed = refreshAccountTypes()) {
                is Resource.Success -> refreshed.data
                else -> emptyList()
            }
        }
        return mergeOwnedTypeCodes(fromApi)
    }

    /**
     * Types used when replacing owned rows in Room. Uses the full owned-type set so stale
     * accounts are cleared even when the latest API page only returned a subset of types.
     */
    private suspend fun ownedTypesForCacheReplace(accounts: List<Account>): List<String> {
        val resolved = resolveOwnedTypeCodes()
        if (resolved.isNotEmpty()) return resolved
        val fromAccounts = accounts.map { it.typeCode.lowercase() }.distinct()
        return fromAccounts.ifEmpty { listOf("default") }
    }

    private suspend fun mergeOwnedTypeCodes(apiTypeCodes: List<String>): List<String> {
        val fromApi = apiTypeCodes
            .map { it.lowercase() }
            .filter { it !in AccountTypeCodes.counterpartyTypeCodes }
        val fromCache = accountDao.getAll().first()
            .map { it.type.lowercase() }
            .filter { it !in AccountTypeCodes.counterpartyTypeCodes }
        return (fromApi + fromCache).distinct()
    }

    private suspend fun fetchAccountDtosPaged(
        types: List<String>?,
    ): List<com.pledgerio.app.data.remote.dto.AccountDto>? {
        val collected = mutableListOf<com.pledgerio.app.data.remote.dto.AccountDto>()
        var offset = 0
        val pageSize = 200
        while (true) {
            val response = apiService.getAccounts(
                type = types?.takeIf { it.isNotEmpty() },
                offset = offset,
                numberOfResults = pageSize,
            )
            if (!response.isSuccessful) {
                return if (collected.isEmpty()) null else collected
            }
            val body = response.body()
            val items = body?.content.orEmpty()
            collected.addAll(items)
            val totalRecords = body?.info?.records ?: 0L
            if (items.isEmpty()) break
            if (totalRecords > 0 && collected.size.toLong() >= totalRecords) break
            if (totalRecords == 0L && items.size < pageSize) break
            offset = collected.size
        }
        return collected.distinctBy { it.id }
    }

    /**
     * True when [cachedPageSize] rows in Room cover the slice `[offset, offset + pageSize)`.
     */
    private fun cacheCoversPage(
        cachedPageSize: Int,
        cachedTotal: Long,
        offset: Int,
        pageSize: Int,
    ): Boolean {
        if (cachedPageSize <= 0 || cachedTotal <= 0L) return false
        val remaining = (cachedTotal - offset).coerceAtLeast(0L)
        val expected = minOf(pageSize.toLong(), remaining).toInt()
        return cachedPageSize >= expected
    }

    private suspend fun fetchCounterpartyPageFromApi(
        offset: Int,
        pageSize: Int,
        nameQuery: String,
    ): Resource<PagedAccounts> {
        return try {
            val response = apiService.getAccounts(
                type = counterpartyTypes,
                accountName = nameQuery.ifBlank { null },
                offset = offset,
                numberOfResults = pageSize,
            )
            if (response.isSuccessful) {
                val body = response.body()
                val items = body?.content?.map { it.toDomain() }?.distinctBy { it.id } ?: emptyList()
                val enriched = enrichWithBalances(items)
                val total = body?.info?.records ?: enriched.size.toLong()
                Resource.Success(
                    PagedAccounts(
                        items = enriched,
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

    private suspend fun ownedFromCache(): List<Account> =
        accountDao.getAll().first()
            .map { it.toDomain() }
            .filter { !AccountTypeCatalog.isCounterparty(it.typeCode) }

    private suspend fun cachedOwnedOrError(message: String): Resource<List<Account>> {
        val owned = ownedFromCache()
        return if (owned.isNotEmpty()) Resource.Success(owned) else Resource.Error(message)
    }

    override suspend fun searchAccounts(
        typeCode: String,
        nameQuery: String,
        limit: Int,
    ): Resource<List<Account>> {
        val isCounterparty = AccountTypeCatalog.isCounterparty(typeCode)
        if (isCounterparty) {
            cacheRefresher.launchIfStale(
                key = SyncKeys.COUNTERPARTY_ACCOUNTS,
                ttlMs = CachePolicy.COUNTERPARTY_ACCOUNTS_TTL_MS,
            ) { refreshCounterpartyAccounts() }
            val cached = accountDao.searchByTypes(
                types = listOf(typeCode.lowercase()),
                query = nameQuery.trim(),
                offset = 0,
                limit = limit,
            ).map { it.toDomain() }
            if (cached.isNotEmpty()) return Resource.Success(cached)
        }
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
        val lowered = typeCodes.map { it.lowercase() }
        val cached = accountDao.getByTypesOnce(lowered).map { it.toDomain() }
        if (cached.isNotEmpty()) {
            return Resource.Success(cached.sortedBy { it.name })
        }
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
                if (accounts.isNotEmpty()) {
                    accountDao.insertAll(accounts.map { AccountEntity.fromDomain(it) })
                }
                Resource.Success(accounts.sortedBy { it.name })
            } else {
                Resource.Error("Failed to fetch accounts: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun getAccount(id: Long): Resource<Account> {
        val cached = accountDao.getById(id)?.toDomain()
        val cacheKey = if (cached != null && AccountTypeCatalog.isCounterparty(cached.typeCode)) {
            SyncKeys.COUNTERPARTY_ACCOUNTS
        } else {
            SyncKeys.OWNED_ACCOUNTS
        }
        val cacheTtl = if (cacheKey == SyncKeys.COUNTERPARTY_ACCOUNTS) {
            CachePolicy.COUNTERPARTY_ACCOUNTS_TTL_MS
        } else {
            CachePolicy.OWNED_ACCOUNTS_TTL_MS
        }
        if (cached != null && !cacheRefresher.isStale(cacheKey, cacheTtl)) {
            return Resource.Success(cached)
        }
        return try {
            val response = apiService.getAccount(id)
            if (response.isSuccessful) {
                val dto = response.body() ?: return Resource.Error("Account not found")
                val account = dto.toDomain()
                val enriched = enrichWithBalances(listOf(account)).first()
                accountDao.insert(AccountEntity.fromDomain(enriched))
                Resource.Success(enriched)
            } else {
                if (cached != null) Resource.Success(cached) else Resource.Error("Failed to fetch account")
            }
        } catch (e: Exception) {
            if (cached != null) Resource.Success(cached) else Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun getAccountTypes(): Resource<List<AccountTypeOption>> {
        val cached = accountTypeDao.getAllCodes()
        cacheRefresher.launchIfStale(
            key = SyncKeys.ACCOUNT_TYPES,
            ttlMs = CachePolicy.ACCOUNT_TYPES_TTL_MS,
        ) { refreshAccountTypes() }
        if (cached.isNotEmpty()) {
            val ownedCodes = cached.filter { it !in AccountTypeCodes.counterpartyTypeCodes }
            return Resource.Success(AccountTypeCatalog.toOptions(ownedCodes))
        }
        // No cache yet — block on a single network call so the picker isn't empty.
        return when (val refreshed = refreshAccountTypes()) {
            is Resource.Success -> {
                val ownedCodes = refreshed.data.filter { it !in AccountTypeCodes.counterpartyTypeCodes }
                Resource.Success(AccountTypeCatalog.toOptions(ownedCodes))
            }
            // Fail-soft: callers (AccountsViewModel / AccountFormViewModel) already render
            // the static counterparty options when the owned list is empty.
            else -> Resource.Success(AccountTypeCatalog.toOptions(emptyList()))
        }
    }

    override suspend fun refreshAccountTypes(): Resource<List<String>> {
        return cacheRefresher.refreshNow(SyncKeys.ACCOUNT_TYPES) {
            try {
                val response = apiService.getAccountTypes()
                if (response.isSuccessful) {
                    val codes = response.body().orEmpty().map { it.lowercase() }
                    accountTypeDao.replaceAll(codes)
                    Resource.Success(codes)
                } else {
                    Resource.Error("Failed to fetch account types: HTTP ${response.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Network error")
            }
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
                invalidateOnMutation(created.typeCode)
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
                invalidateOnMutation(account.typeCode)
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
                val cached = accountDao.getById(id)
                accountDao.deleteById(id)
                cached?.type?.let { invalidateOnMutation(it) }
                Resource.Success(Unit)
            } else {
                Resource.Error("Failed to delete account: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    private fun invalidateOnMutation(typeCode: String) {
        if (AccountTypeCatalog.isCounterparty(typeCode)) {
            cacheRefresher.refreshInBackground(SyncKeys.COUNTERPARTY_ACCOUNTS) { refreshCounterpartyAccounts() }
        } else {
            cacheRefresher.refreshInBackground(SyncKeys.OWNED_ACCOUNTS) { refreshOwnedAccounts() }
        }
    }

    private fun com.pledgerio.app.data.remote.dto.AccountDto.toDomain(): Account {
        return Account(
            id = id,
            name = name,
            description = description ?: "",
            currency = account?.currency ?: "EUR",
            typeCode = type.lowercase(),
            iconFileCode = iconFileCode,
            iban = account?.iban,
            bic = account?.bic,
            lastActivity = history?.lastTransaction,
        )
    }
}
