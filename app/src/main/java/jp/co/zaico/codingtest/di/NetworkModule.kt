package jp.co.zaico.codingtest.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import jp.co.zaico.codingtest.R
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(Android)

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    @ApiBaseUrl
    fun provideApiBaseUrl(@ApplicationContext context: Context): String =
        context.getString(R.string.api_endpoint)

    @Provides
    @Singleton
    @ApiToken
    fun provideApiToken(@ApplicationContext context: Context): String =
        context.getString(R.string.api_token)

    @Provides
    @Singleton
    @CompaniesPath
    fun provideCompaniesPath(@ApplicationContext context: Context): String =
        context.getString(R.string.companies_path)
}
