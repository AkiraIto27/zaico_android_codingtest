package jp.co.zaico.codingtest.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.zaico.codingtest.data.remote.CompanyRemoteService
import jp.co.zaico.codingtest.data.remote.InventoryCreateRemoteService
import jp.co.zaico.codingtest.data.remote.InventoryRemoteService
import jp.co.zaico.codingtest.data.remote.KtorCompanyRemoteService
import jp.co.zaico.codingtest.data.remote.KtorInventoryRemoteService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteModule {

    @Binds
    @Singleton
    abstract fun bindCompanyRemote(
        remote: KtorCompanyRemoteService
    ): CompanyRemoteService

    @Binds
    @Singleton
    abstract fun bindInventoryRemote(
        remote: KtorInventoryRemoteService
    ): InventoryRemoteService

    @Binds
    @Singleton
    abstract fun bindInventoryCreateRemote(
        remote: KtorInventoryRemoteService
    ): InventoryCreateRemoteService
}
