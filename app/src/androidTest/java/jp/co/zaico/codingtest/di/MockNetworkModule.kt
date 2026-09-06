package jp.co.zaico.codingtest.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class]
)
object MockNetworkModule {

    val control = MockNetworkControl

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(MockEngine { request -> respondTo(request) })

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    @ApiBaseUrl
    fun provideApiBaseUrl(): String = "https://mock.zaico.test"

    @Provides
    @Singleton
    @ApiToken
    fun provideApiToken(): String = "synthetic-test-token"

    @Provides
    @Singleton
    @CompaniesPath
    fun provideCompaniesPath(): String = "/api/v2/orgs/companies.json"

    private suspend fun MockRequestHandleScope.respondTo(request: HttpRequestData) = run {
        check(request.headers[HttpHeaders.Authorization] == "Bearer synthetic-test-token")
        when (request.method to request.url.encodedPath) {
            HttpMethod.Get to "/api/v2/orgs/companies.json" -> respond(
                content = """{"data":[{"id":123}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
            HttpMethod.Get to "/api/v2/orgs/companies/123/inventories.json" -> respond(
                content = control.listResponse(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
            HttpMethod.Get to "/api/v2/orgs/companies/123/inventories/77.json" -> respond(
                content = """{"data":{"id":77,"title":"Shelf","quantity":"1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
            HttpMethod.Post to "/api/v2/orgs/companies/123/inventories.json" -> respond(
                content = """{"data":{"id":99}}""",
                status = HttpStatusCode.Created,
                headers = jsonHeaders
            )
            else -> error("Unexpected MockEngine request: ${request.method} ${request.url}")
        }
    }

    private val jsonHeaders = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString()
    )
}

object MockNetworkControl {
    var blockInventoryList = false
    lateinit var listRequestStarted: CompletableDeferred<Unit>
    lateinit var releaseInventoryList: CompletableDeferred<Unit>

    fun reset() {
        blockInventoryList = false
        listRequestStarted = CompletableDeferred()
        releaseInventoryList = CompletableDeferred()
    }

    suspend fun listResponse(): String {
        if (blockInventoryList) {
            listRequestStarted.complete(Unit)
            releaseInventoryList.await()
        }
        return """{"data":[{"id":77,"title":"Shelf","quantity":"1"}]}"""
    }
}
