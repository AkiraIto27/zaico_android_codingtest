package jp.co.zaico.codingtest.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import jp.co.zaico.codingtest.data.remote.CompanyRemoteResult
import jp.co.zaico.codingtest.data.remote.CompanyRemoteService
import jp.co.zaico.codingtest.data.remote.InventoryCreateRemoteResult
import jp.co.zaico.codingtest.data.remote.InventoryCreateRemoteService
import jp.co.zaico.codingtest.data.remote.KtorCompanyRemoteService
import jp.co.zaico.codingtest.data.remote.KtorInventoryRemoteService
import jp.co.zaico.codingtest.data.remote.dto.CompanyRemoteCompany
import jp.co.zaico.codingtest.domain.company.CompanyIdResult
import jp.co.zaico.codingtest.domain.company.CompanyRepository
import jp.co.zaico.codingtest.domain.result.CreateInventoryResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class KtorInventoryCreatorTest {

    @Test
    fun 作成Remoteを差し替えた場合_注入したRemoteへ委譲する() = runTest {
        val client = HttpClient(MockEngine { error("unused") })
        var receivedCompanyId: Int? = null
        var receivedTitle: String? = null
        val companyRepository = DefaultCompanyRepository(
            remote = object : CompanyRemoteService {
                override suspend fun getCompanies(): CompanyRemoteResult =
                    CompanyRemoteResult.Success(listOf(CompanyRemoteCompany(321)))
            },
            token = "synthetic-test-token"
        )
        val createRemote = object : InventoryCreateRemoteService {
            override suspend fun createInventory(
                companyId: Int,
                title: String
            ): InventoryCreateRemoteResult {
                receivedCompanyId = companyId
                receivedTitle = title
                return InventoryCreateRemoteResult.Success(987L)
            }
        }
        val creator = DefaultInventoryCreator(
            token = "synthetic-test-token",
            companyRepository = companyRepository,
            remote = createRemote
        )

        assertEquals(
            CreateInventoryResult.Success(987L),
            creator.createInventory("Injected inventory")
        )
        assertEquals(321, receivedCompanyId)
        assertEquals("Injected inventory", receivedTitle)
        assertTrue(client.coroutineContext.isActive)
        client.close()
    }

    @Test
    fun 先頭の拠点IDで作成する場合_V2の認証付きtitleのみをPOSTする() = runTest {
        listOf(HttpStatusCode.OK, HttpStatusCode.Created).forEach { successStatus ->
            val fixture = fixture {
                respond(
                    content = """{"data":{"id":42},"ignored":{"value":true}}""",
                    status = successStatus,
                    headers = jsonHeaders
                )
            }

            val result = fixture.creator.createInventory("  New inventory  ")

            assertEquals(CreateInventoryResult.Success(42L), result)
            assertEquals(1, fixture.getCount)
            assertEquals(1, fixture.postCount)
            assertEquals(URLProtocol.HTTPS, fixture.lastPostRequest?.url?.protocol)
            assertEquals("web.zaico.co.jp", fixture.lastPostRequest?.url?.host)
            assertEquals(
                "/api/v2/orgs/companies/123/inventories.json",
                fixture.lastPostRequest?.url?.encodedPath
            )
            assertEquals(
                "Bearer synthetic-test-token",
                fixture.lastPostRequest?.headers?.get(HttpHeaders.Authorization)
            )
            assertEquals(ContentType.Application.Json, fixture.lastPostRequest?.body?.contentType)
            val body = Json.parseToJsonElement(
                (fixture.lastPostRequest?.body as TextContent).text
            ).jsonObject
            assertEquals(setOf("title"), body.keys)
            assertEquals("  New inventory  ", body.getValue("title").jsonPrimitive.content)
            assertFalse((fixture.lastPostRequest?.body as TextContent).text.contains("synthetic-test-token"))
            fixture.client.close()
        }
    }

    @Test
    fun baseUrl末尾のスラッシュ有無が異なる場合_正しいV2URLを組み立てる() = runTest {
        listOf("https://web.zaico.co.jp", "https://web.zaico.co.jp/").forEach { baseUrl ->
            val fixture = fixture(baseUrl = baseUrl)

            assertEquals(
                CreateInventoryResult.Success(42L),
                fixture.creator.createInventory("Slash-safe inventory")
            )
            assertEquals(
                "/api/v2/orgs/companies/123/inventories.json",
                fixture.lastPostRequest?.url?.encodedPath
            )
            assertEquals(1, fixture.getCount)
            assertEquals(1, fixture.postCount)
            fixture.client.close()
        }
    }

    @Test
    fun data_idが0負数または数値文字列の場合_200と201を成功としてIDを保持する() = runTest {
        listOf(
            "0" to 0L,
            "-7" to -7L,
            "\"42\"" to 42L
        ).forEach { (rawId, expectedId) ->
            listOf(HttpStatusCode.OK, HttpStatusCode.Created).forEach { status ->
                val fixture = fixture(
                    post = {
                        respond(
                            content = """{"data":{"id":$rawId}}""",
                            status = status,
                            headers = jsonHeaders
                        )
                    }
                )

                assertEquals(
                    CreateInventoryResult.Success(expectedId),
                    fixture.creator.createInventory("Boundary inventory")
                )
                assertEquals(1, fixture.getCount)
                assertEquals(1, fixture.postCount)
                fixture.client.close()
            }
        }
    }

    @Test
    fun HTTP解析通信または拠点取得に失敗した場合_対応する失敗へ分類して再送しない() = runTest {
        listOf(
            HttpStatusCode.NoContent,
            HttpStatusCode.BadRequest,
            HttpStatusCode.Unauthorized,
            HttpStatusCode.Forbidden,
            HttpStatusCode.InternalServerError
        ).forEach { status ->
            val fixture = fixture {
                respond(content = "ignored", status = status)
            }
            assertEquals(
                CreateInventoryResult.HttpFailure(status.value),
                fixture.creator.createInventory("Valid")
            )
            assertEquals(1, fixture.getCount)
            assertEquals(1, fixture.postCount)
            fixture.client.close()
        }

        listOf(HttpStatusCode.OK, HttpStatusCode.Created).forEach { status ->
            listOf(
                "not-json",
                "[]",
                "{}",
                """{"data":null}""",
                """{"data":{}}""",
                """{"data":{"id":null}}""",
                """{"data":{"id":{"invalid":true}}}""",
                """{"data":{"id":"not-a-number"}}"""
            ).forEach { body ->
                val fixture = fixture {
                    respond(content = body, status = status, headers = jsonHeaders)
                }
                assertEquals(
                    CreateInventoryResult.DecodeFailure,
                    fixture.creator.createInventory("Valid")
                )
                assertEquals(1, fixture.getCount)
                assertEquals(1, fixture.postCount)
                fixture.client.close()
            }
        }

        val networkFixture = fixture(post = {
            throw IOException("synthetic transport failure")
        })
        assertEquals(
            CreateInventoryResult.NetworkFailure,
            networkFixture.creator.createInventory("Valid")
        )
        assertEquals(1, networkFixture.getCount)
        assertEquals(1, networkFixture.postCount)
        networkFixture.client.close()

        val cancellationFixture = fixture(post = {
            throw CancellationException("synthetic cancellation")
        })
        try {
            cancellationFixture.creator.createInventory("Valid")
            fail("CancellationException was not rethrown")
        } catch (_: CancellationException) {
            // Cancellation is not a network failure.
        } finally {
            assertEquals(1, cancellationFixture.getCount)
            assertEquals(1, cancellationFixture.postCount)
            cancellationFixture.client.close()
        }

        listOf(
            CompanyIdResult.Empty to CreateInventoryResult.EmptyCompany,
            CompanyIdResult.HttpFailure(503) to CreateInventoryResult.HttpFailure(503),
            CompanyIdResult.DecodeFailure to CreateInventoryResult.DecodeFailure,
            CompanyIdResult.NetworkFailure to CreateInventoryResult.NetworkFailure
        ).forEach { (companyFailure, expected) ->
            val fixture = fixture(company = {
                when (companyFailure) {
                    CompanyIdResult.Empty -> respond("""{"data":[]}""", HttpStatusCode.OK, jsonHeaders)
                    is CompanyIdResult.HttpFailure -> respond("ignored", HttpStatusCode.ServiceUnavailable)
                    CompanyIdResult.DecodeFailure -> respond("not-json", HttpStatusCode.OK, jsonHeaders)
                    CompanyIdResult.NetworkFailure -> throw IOException("synthetic company failure")
                    is CompanyIdResult.Success,
                    CompanyIdResult.ConfigurationFailure -> error("unused company result")
                }
            })
            assertEquals(expected, fixture.creator.createInventory("Valid"))
            assertEquals(1, fixture.getCount)
            assertEquals(0, fixture.postCount)
            fixture.client.close()
        }

        val blankToken = fixture(token = "   ")
        assertEquals(CreateInventoryResult.ConfigurationFailure, blankToken.creator.createInventory("Valid"))
        assertEquals(0, blankToken.getCount)
        assertEquals(0, blankToken.postCount)
        blankToken.client.close()
    }

    @Test
    fun 先頭拠点が正常で後続が不正でも_先頭の拠点IDを取得する() = runTest {
        val fixture = companyRepositoryFixture {
            respond(
                content = """{"data":[{"id":123,"name":"Main"},"malformed"]}""",
                headers = jsonHeaders
            )
        }

        try {
            assertEquals(CompanyIdResult.Success(123), fixture.repository.getCompanyId())
            assertEquals(1, fixture.requestCount.get())
        } finally {
            fixture.client.close()
        }
    }

    @Test
    fun HTTPエラー応答の場合_ステータスを保持したHttpFailureを返す() = runTest {
        val fixture = companyRepositoryFixture {
            respond(content = "not-json", status = HttpStatusCode.BadRequest)
        }

        try {
            assertEquals(
                CompanyIdResult.HttpFailure(HttpStatusCode.BadRequest.value),
                fixture.repository.getCompanyId()
            )
            assertEquals(1, fixture.requestCount.get())
        } finally {
            fixture.client.close()
        }
    }

    @Test
    fun API呼び出しで例外が発生した場合_NetworkFailureを返す() = runTest {
        val fixture = companyRepositoryFixture {
            throw IOException("synthetic transport failure")
        }

        try {
            assertEquals(CompanyIdResult.NetworkFailure, fixture.repository.getCompanyId())
            assertEquals(1, fixture.requestCount.get())
        } finally {
            fixture.client.close()
        }
    }

    @Test
    fun 本文取得で例外が発生した場合_NetworkFailureを返す() = runTest {
        val body = ByteChannel().apply {
            close(IOException("synthetic body failure"))
        }
        val fixture = companyRepositoryFixture {
            respond(content = body, headers = jsonHeaders)
        }

        try {
            assertEquals(CompanyIdResult.NetworkFailure, fixture.repository.getCompanyId())
            assertEquals(1, fixture.requestCount.get())
        } finally {
            fixture.client.close()
        }
    }

    @Test
    fun 不正なJSONの場合_DecodeFailureを返す() = runTest {
        val fixture = companyRepositoryFixture {
            respond(content = "not-json", headers = jsonHeaders)
        }

        try {
            assertEquals(CompanyIdResult.DecodeFailure, fixture.repository.getCompanyId())
            assertEquals(1, fixture.requestCount.get())
        } finally {
            fixture.client.close()
        }
    }

    @Test
    fun 空一覧の場合_Emptyを返す() = runTest {
        val fixture = companyRepositoryFixture {
            respond(content = """{"data":[]}""", headers = jsonHeaders)
        }

        try {
            assertEquals(CompanyIdResult.Empty, fixture.repository.getCompanyId())
            assertEquals(1, fixture.requestCount.get())
        } finally {
            fixture.client.close()
        }
    }

    @Test
    fun 先頭拠点のIDが欠落した場合_Emptyを返す() = runTest {
        val fixture = companyRepositoryFixture {
            respond(content = """{"data":[{"name":"Main"}]}""", headers = jsonHeaders)
        }

        try {
            assertEquals(CompanyIdResult.Empty, fixture.repository.getCompanyId())
            assertEquals(1, fixture.requestCount.get())
        } finally {
            fixture.client.close()
        }
    }

    @Test
    fun キャンセル例外が発生した場合_CancellationExceptionを再送出する() = runTest {
        val fixture = companyRepositoryFixture {
            throw CancellationException("synthetic cancellation")
        }

        try {
            fixture.repository.getCompanyId()
            fail("CancellationException was not rethrown")
        } catch (exception: CancellationException) {
            assertEquals("synthetic cancellation", exception.message)
        } finally {
            assertEquals(1, fixture.requestCount.get())
            fixture.client.close()
        }
    }

    @Test
    fun 成功結果を再取得する場合_キャッシュを利用しAPI呼び出しを1回にする() = runTest {
        val fixture = companyRepositoryFixture {
            respond(content = """{"data":[{"id":123}]}""", headers = jsonHeaders)
        }

        try {
            val first = fixture.repository.getCompanyId()
            val second = fixture.repository.getCompanyId()

            assertEquals(CompanyIdResult.Success(123), first)
            assertEquals(first, second)
            assertEquals(1, fixture.requestCount.get())
        } finally {
            fixture.client.close()
        }
    }

    @Test
    fun Empty結果を再取得する場合_キャッシュを利用しAPI呼び出しを1回にする() = runTest {
        val fixture = companyRepositoryFixture {
            respond(content = """{"data":[]}""", headers = jsonHeaders)
        }

        try {
            assertEquals(CompanyIdResult.Empty, fixture.repository.getCompanyId())
            assertEquals(CompanyIdResult.Empty, fixture.repository.getCompanyId())
            assertEquals(1, fixture.requestCount.get())
        } finally {
            fixture.client.close()
        }
    }

    @Test
    fun 同時に拠点IDを取得する場合_MutexでAPI呼び出しを1回にする() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val requestCount = AtomicInteger()
        val client = HttpClient(MockEngine { request ->
            requestCount.incrementAndGet()
            assertEquals(URLProtocol.HTTPS, request.url.protocol)
            assertEquals("web.zaico.co.jp", request.url.host)
            assertEquals("/api/v2/orgs/companies.json", request.url.encodedPath)
            assertEquals(
                "Bearer synthetic-test-token",
                request.headers[HttpHeaders.Authorization]
            )
            requestStarted.complete(Unit)
            releaseResponse.await()
            respond(content = """{"data":[{"id":123}]}""", headers = jsonHeaders)
        })
        val repository = DefaultCompanyRepository(
            remote = KtorCompanyRemoteService(
                client = client,
                baseUrl = "https://web.zaico.co.jp/",
                token = "synthetic-test-token",
                companiesPath = "/api/v2/orgs/companies.json"
            ),
            token = "synthetic-test-token"
        )

        try {
            val first = async { repository.getCompanyId() }
            requestStarted.await()
            val second = async { repository.getCompanyId() }

            assertFalse(second.isCompleted)
            releaseResponse.complete(Unit)

            assertEquals(CompanyIdResult.Success(123), first.await())
            assertEquals(CompanyIdResult.Success(123), second.await())
            assertEquals(1, requestCount.get())
        } finally {
            client.close()
        }
    }

    private class Fixture(
        val creator: DefaultInventoryCreator,
        val client: HttpClient,
        var getCount: Int = 0,
        var postCount: Int = 0,
        var lastPostRequest: HttpRequestData? = null
    )

    private fun fixture(
        baseUrl: String = "https://web.zaico.co.jp/",
        token: String = "synthetic-test-token",
        company: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData = {
            respond(
                content = """{"data":[{"id":123},{"id":999}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        },
        post: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData = {
            respond(
                content = """{"data":{"id":42}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }
    ): Fixture {
        lateinit var fixture: Fixture
        val client = HttpClient(MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> {
                    fixture.getCount += 1
                    assertEquals(URLProtocol.HTTPS, request.url.protocol)
                    assertEquals("web.zaico.co.jp", request.url.host)
                    assertTrue(request.url.parameters.isEmpty())
                    assertEquals("/api/v2/orgs/companies.json", request.url.encodedPath)
                    assertEquals(
                        "Bearer $token",
                        request.headers[HttpHeaders.Authorization]
                    )
                    company(request)
                }
                HttpMethod.Post -> {
                    fixture.postCount += 1
                    fixture.lastPostRequest = request
                    assertEquals(URLProtocol.HTTPS, request.url.protocol)
                    assertEquals("web.zaico.co.jp", request.url.host)
                    assertTrue(request.url.parameters.isEmpty())
                    assertFalse(request.url.toString().contains(token))
                    post(request)
                }
                else -> error("Unexpected method: ${request.method}")
            }
        })
        val companyRepository = DefaultCompanyRepository(
            remote = KtorCompanyRemoteService(
                client = client,
                baseUrl = baseUrl,
                token = token,
                companiesPath = "/api/v2/orgs/companies.json"
            ),
            token = token
        )
        fixture = Fixture(
            creator = DefaultInventoryCreator(
                token = token,
                companyRepository = companyRepository,
                remote = KtorInventoryRemoteService(
                    client = client,
                    baseUrl = baseUrl,
                    token = token
                )
            ),
            client = client
        )
        return fixture
    }

    private class CompanyRepositoryFixture(
        val repository: CompanyRepository,
        val client: HttpClient,
        val requestCount: AtomicInteger
    )

    private fun companyRepositoryFixture(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData
    ): CompanyRepositoryFixture {
        val requestCount = AtomicInteger()
        val client = HttpClient(MockEngine { request ->
            requestCount.incrementAndGet()
            assertEquals(URLProtocol.HTTPS, request.url.protocol)
            assertEquals("web.zaico.co.jp", request.url.host)
            assertEquals("/api/v2/orgs/companies.json", request.url.encodedPath)
            assertEquals(
                "Bearer synthetic-test-token",
                request.headers[HttpHeaders.Authorization]
            )
            handler(request)
        })
        return CompanyRepositoryFixture(
            repository = DefaultCompanyRepository(
                remote = KtorCompanyRemoteService(
                    client = client,
                    baseUrl = "https://web.zaico.co.jp/",
                    token = "synthetic-test-token",
                    companiesPath = "/api/v2/orgs/companies.json"
                ),
                token = "synthetic-test-token"
            ),
            client = client,
            requestCount = requestCount
        )
    }

    private companion object {
        val jsonHeaders = headersOf(
            HttpHeaders.ContentType,
            ContentType.Application.Json.toString()
        )
    }
}
