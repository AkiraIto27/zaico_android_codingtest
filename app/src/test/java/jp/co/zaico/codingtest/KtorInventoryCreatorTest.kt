package jp.co.zaico.codingtest

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class KtorInventoryCreatorTest {

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
            fixture.creator.close()
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
            fixture.creator.close()
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
            fixture.creator.close()
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
                fixture.creator.close()
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
        networkFixture.creator.close()

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
            cancellationFixture.creator.close()
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
            fixture.creator.close()
        }

        val blankToken = fixture(token = "   ")
        assertEquals(CreateInventoryResult.ConfigurationFailure, blankToken.creator.createInventory("Valid"))
        assertEquals(0, blankToken.getCount)
        assertEquals(0, blankToken.postCount)
        blankToken.creator.close()
    }

    private class Fixture(
        val creator: KtorInventoryCreator,
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
        fixture = Fixture(
            creator = KtorInventoryCreator(
                client = client,
                baseUrl = baseUrl,
                token = token,
                companyRepository = CompanyRepository(
                    client = client,
                    baseUrl = baseUrl,
                    token = token,
                    companiesPath = "/api/v2/orgs/companies.json"
                )
            ),
            client = client
        )
        return fixture
    }

    private companion object {
        val jsonHeaders = headersOf(
            HttpHeaders.ContentType,
            ContentType.Application.Json.toString()
        )
    }
}
