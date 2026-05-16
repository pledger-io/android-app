package com.pledgerio.app.data.remote.api

import com.pledgerio.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface PledgerApiService {

    // Authentication
    @POST("v2/api/security/authenticate")
    suspend fun authenticate(@Body request: LoginRequest): Response<LoginResponse>

    @POST("v2/api/security/oauth")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<LoginResponse>

    @POST("v2/api/security/logout")
    suspend fun logout(): Response<Unit>

    // OpenID Connect
    @GET(".well-known/openid-connect")
    suspend fun getOpenIdConfig(): Response<OpenIdConfigResponse>

    // User / Auth Sessions (requires authentication)
    @GET("v2/api/user-account/{user}/sessions")
    suspend fun listSessions(@Path("user") user: String): Response<List<SessionResponse>>

    @POST("v2/api/user-account/{user}/sessions")
    suspend fun createSession(
        @Path("user") user: String,
        @Body request: SessionRequest,
    ): Response<SessionResponse>

    @POST("v2/api/user-account/verify-2-factor")
    suspend fun verify2Factor(@Body request: Map<String, String>): Response<Any>

    // Profile
    @GET("v2/api/user-account/{user}")
    suspend fun getProfile(@Path("user") user: String): Response<UserProfileResponse>

    // Accounts
    @GET("v2/api/accounts")
    suspend fun getAccounts(
        @Query("type") type: List<String>? = null,
        @Query("accountName") accountName: String? = null,
        @Query("offset") offset: Int,
        @Query("numberOfResults") numberOfResults: Int,
    ): Response<AccountPagedResponse>

    @GET("v2/api/accounts/{id}")
    suspend fun getAccount(@Path("id") id: Long): Response<AccountDto>

    @POST("v2/api/accounts")
    suspend fun createAccount(@Body request: CreateAccountRequest): Response<AccountDto>

    @PUT("v2/api/accounts/{id}")
    suspend fun updateAccount(
        @Path("id") id: Long,
        @Body request: CreateAccountRequest,
    ): Response<AccountDto>

    @DELETE("v2/api/accounts/{id}")
    suspend fun deleteAccount(@Path("id") id: Long): Response<Unit>

    @GET("v2/api/account-types")
    suspend fun getAccountTypes(): Response<List<String>>

    // Transactions
    @GET("v2/api/transactions")
    suspend fun getTransactions(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("account") accounts: List<Long>? = null,
        @Query("type") type: String? = null,
        @Query("description") description: String? = null,
        @Query("currency") currency: String? = null,
        @Query("expense") expenses: List<Long>? = null,
        @Query("category") categories: List<Long>? = null,
        @Query("contract") contracts: List<Long>? = null,
        @Query("offset") offset: Int = 0,
        @Query("numberOfResults") numberOfResults: Int = 25,
    ): Response<TransactionPagedResponse>

    @GET("v2/api/transactions/{id}")
    suspend fun getTransaction(@Path("id") id: Long): Response<TransactionDto>

    @POST("v2/api/transactions")
    suspend fun createTransaction(@Body request: CreateTransactionRequest): Response<TransactionDto>

    @PUT("v2/api/transactions/{id}")
    suspend fun updateTransaction(
        @Path("id") id: Long,
        @Body request: CreateTransactionRequest,
    ): Response<TransactionDto>

    @PATCH("v2/api/transactions/{id}")
    suspend fun patchTransaction(
        @Path("id") id: Long,
        @Body splits: List<TransactionSplitDto>,
    ): Response<TransactionDto>

    @DELETE("v2/api/transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: Long): Response<Unit>

    // Categories
    @GET("v2/api/categories")
    suspend fun getCategories(
        @Query("name") name: String? = null,
        @Query("offset") offset: Int? = null,
        @Query("numberOfResults") numberOfResults: Int? = null,
    ): Response<CategoryPagedResponse>

    @GET("v2/api/categories/{id}")
    suspend fun getCategory(@Path("id") id: Long): Response<CategoryDto>

    // Budgets
    @GET("v2/api/budgets")
    suspend fun getBudgets(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
        @Query("firstOnly") firstOnly: Boolean? = null,
    ): Response<BudgetDto>

    @POST("v2/api/budgets")
    suspend fun createInitialBudget(@Body request: CreateBudgetRequest): Response<BudgetDto>

    @GET("v2/api/budgets/expenses")
    suspend fun getExpenses(
        @Query("name") name: String? = null,
    ): Response<List<ExpenseDto>>

    @PATCH("v2/api/budgets/expenses")
    suspend fun saveExpense(@Body request: ExpenseRequest): Response<BudgetDto>

    @GET("v2/api/budgets/expenses/balance")
    suspend fun getExpenseBalance(
        @Query("year") year: Int,
        @Query("month") month: Int,
        @Query("expenseId") expenseIds: List<Long>? = null,
    ): Response<List<ExpenseComputedDto>>

    // Balance / Statistics
    @POST("v2/api/balance")
    suspend fun getBalance(@Body request: BalanceRequest): Response<BalanceDto>

    @POST("v2/api/balance/{partition}")
    suspend fun getPartitionedBalance(
        @Path("partition") partition: String,
        @Body request: BalanceRequest,
    ): Response<List<BalancePartitionedDto>>

    @POST("v2/api/balance/by-date/{type}")
    suspend fun getDatedBalance(
        @Path("type") type: String,
        @Body request: BalanceRequest,
    ): Response<List<BalanceDatedDto>>

    // Contracts
    @GET("v2/api/contracts")
    suspend fun getContracts(
        @Query("name") name: String? = null,
        @Query("status") status: String? = null,
    ): Response<List<ContractDto>>

    // Currencies
    @GET("v2/api/currencies")
    suspend fun getCurrencies(): Response<List<CurrencyDto>>

    // Health
    @GET("health")
    suspend fun health(): Response<Any>
}
