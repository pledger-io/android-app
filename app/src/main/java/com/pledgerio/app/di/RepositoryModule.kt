package com.pledgerio.app.di

import com.pledgerio.app.accounts.data.AccountRepositoryImpl
import com.pledgerio.app.identity.data.AuthRepositoryImpl
import com.pledgerio.app.budgets.data.BudgetRepositoryImpl
import com.pledgerio.app.catalog.data.CategoryRepositoryImpl
import com.pledgerio.app.catalog.data.ContractRepositoryImpl
import com.pledgerio.app.catalog.data.CurrencyRepositoryImpl
import com.pledgerio.app.data.repository.IssueReportRepositoryImpl
import com.pledgerio.app.data.repository.ReportRepositoryImpl
import com.pledgerio.app.data.repository.SpendingInsightRepositoryImpl
import com.pledgerio.app.catalog.data.TagRepositoryImpl
import com.pledgerio.app.data.repository.TransactionOutboxRepositoryImpl
import com.pledgerio.app.transactions.data.TransactionRepositoryImpl
import com.pledgerio.app.identity.data.UserSessionRepositoryImpl
import com.pledgerio.app.data.cache.ReportsOverviewCache
import com.pledgerio.app.transactions.data.ocr.InvoiceTextExtractor
import com.pledgerio.app.accounts.domain.repository.AccountRepository
import com.pledgerio.app.identity.domain.repository.AuthRepository
import com.pledgerio.app.budgets.domain.repository.BudgetRepository
import com.pledgerio.app.catalog.domain.repository.CategoryRepository
import com.pledgerio.app.catalog.domain.repository.ContractRepository
import com.pledgerio.app.catalog.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.IssueReportRepository
import com.pledgerio.app.transactions.domain.repository.InvoiceTextReader
import com.pledgerio.app.domain.repository.ReportRepository
import com.pledgerio.app.domain.repository.ReportsOverviewStore
import com.pledgerio.app.domain.repository.SpendingInsightRepository
import com.pledgerio.app.catalog.domain.repository.TagRepository
import com.pledgerio.app.domain.repository.TransactionOutboxRepository
import com.pledgerio.app.transactions.domain.repository.TransactionRepository
import com.pledgerio.app.identity.domain.repository.UserSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserSessionRepository(impl: UserSessionRepositoryImpl): UserSessionRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindTransactionOutboxRepository(
        impl: TransactionOutboxRepositoryImpl,
    ): TransactionOutboxRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindCurrencyRepository(impl: CurrencyRepositoryImpl): CurrencyRepository

    @Binds
    @Singleton
    abstract fun bindContractRepository(impl: ContractRepositoryImpl): ContractRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindIssueReportRepository(impl: IssueReportRepositoryImpl): IssueReportRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository

    @Binds
    @Singleton
    abstract fun bindReportsOverviewStore(impl: ReportsOverviewCache): ReportsOverviewStore

    @Binds
    @Singleton
    abstract fun bindSpendingInsightRepository(
        impl: SpendingInsightRepositoryImpl,
    ): SpendingInsightRepository

    @Binds
    @Singleton
    abstract fun bindInvoiceTextReader(impl: InvoiceTextExtractor): InvoiceTextReader
}
