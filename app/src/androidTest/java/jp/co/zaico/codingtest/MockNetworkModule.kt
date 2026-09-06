package jp.co.zaico.codingtest

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import jp.co.zaico.codingtest.di.ApiBaseUrl
import jp.co.zaico.codingtest.di.ApiToken
import jp.co.zaico.codingtest.di.CompaniesPath
import jp.co.zaico.codingtest.di.NetworkModule
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class]
)
object MockNetworkModule {
    private val jsonHeaders = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString()
    )

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(MockEngine { request ->
        when {
            request.method == HttpMethod.Get &&
                request.url.encodedPath == "/api/v2/orgs/companies.json" ->
                respond("""{"data":[{"id":123}]}""", HttpStatusCode.OK, jsonHeaders)

            request.method == HttpMethod.Get &&
                request.url.encodedPath.endsWith("/inventories.json") ->
                respond("""{"data":[]}""", HttpStatusCode.OK, jsonHeaders)

            request.method == HttpMethod.Get ->
                respond(
                    """{"data":{"id":77,"title":"Shelf","quantity":"1"}}""",
                    HttpStatusCode.OK,
                    jsonHeaders
                )

            request.method == HttpMethod.Post ->
                respond("""{"data":{"id":42}}""", HttpStatusCode.Created, jsonHeaders)

            else -> error("Unexpected request: ${request.method} ${request.url}")
        }
    })

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @ApiBaseUrl
    fun provideApiBaseUrl(): String = "https://web.zaico.co.jp"

    @Provides
    @ApiToken
    fun provideApiToken(): String = "synthetic-test-token"

    @Provides
    @CompaniesPath
    fun provideCompaniesPath(): String = "/api/v2/orgs/companies.json"
}
