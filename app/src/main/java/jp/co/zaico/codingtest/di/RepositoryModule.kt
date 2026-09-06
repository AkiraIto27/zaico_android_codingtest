package jp.co.zaico.codingtest.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.zaico.codingtest.data.repository.CompanyRepositoryImpl
import jp.co.zaico.codingtest.data.repository.DefaultInventoryRepository
import jp.co.zaico.codingtest.data.repository.InventoryCreator
import jp.co.zaico.codingtest.data.repository.KtorInventoryCreator
import jp.co.zaico.codingtest.domain.company.CompanyRepository
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    @Binds
    @Singleton
    internal abstract fun bindCompanyRepository(repository: CompanyRepositoryImpl): CompanyRepository

    @Binds
    @Singleton
    internal abstract fun bindInventoryRepository(repository: DefaultInventoryRepository): InventoryRepository

    @Binds
    @Singleton
    internal abstract fun bindInventoryCreator(creator: KtorInventoryCreator): InventoryCreator
}
