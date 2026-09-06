package jp.co.zaico.codingtest.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.zaico.codingtest.data.repository.DefaultCompanyRepository
import jp.co.zaico.codingtest.data.repository.DefaultInventoryCreator
import jp.co.zaico.codingtest.data.repository.DefaultInventoryRepository
import jp.co.zaico.codingtest.domain.company.CompanyRepository
import jp.co.zaico.codingtest.domain.inventory.InventoryCreator
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCompanyRepository(
        repository: DefaultCompanyRepository
    ): CompanyRepository

    @Binds
    @Singleton
    abstract fun bindInventoryRepository(
        repository: DefaultInventoryRepository
    ): InventoryRepository

    @Binds
    @Singleton
    abstract fun bindInventoryCreator(
        creator: DefaultInventoryCreator
    ): InventoryCreator
}
