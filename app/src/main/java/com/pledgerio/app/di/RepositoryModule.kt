package com.pledgerio.app.di

import com.pledgerio.app.data.repository.AccountRepositoryImpl
import com.pledgerio.app.data.repository.AuthRepositoryImpl
import com.pledgerio.app.data.repository.BudgetRepositoryImpl
import com.pledgerio.app.data.repository.CategoryRepositoryImpl
import com.pledgerio.app.data.repository.ContractRepositoryImpl
import com.pledgerio.app.data.repository.CurrencyRepositoryImpl
import com.pledgerio.app.data.repository.TagRepositoryImpl
import com.pledgerio.app.data.repository.TransactionRepositoryImpl
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TagRepository
import com.pledgerio.app.domain.repository.TransactionRepository
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
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

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
}
