package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class KtorInventoryCreatorTest {

    @Test
    fun `200と正常なdata_id_在庫作成が成功する`() = assertSuccessfulCreate(HttpStatusCode.OK)

    @Test
    fun `201と正常なdata_id_在庫作成が成功する`() = assertSuccessfulCreate(HttpStatusCode.Created)

    private fun assertSuccessfulCreate(successStatus: HttpStatusCode) = runBlocking {
        var requestCount = 0
        var postRequestCount = 0
        val engine = MockEngine { request ->
            requestCount += 1
            assertEquals(URLProtocol.HTTPS, request.url.protocol)
            assertEquals("web.zaico.co.jp", request.url.host)
            assertTrue(request.url.parameters.isEmpty())
            assertEquals("Bearer synthetic-test-token", request.headers[HttpHeaders.Authorization])
            assertFalse(request.url.toString().contains("synthetic-test-token"))

            if (request.method == HttpMethod.Get) {
                assertEquals("/api/v2/orgs/companies.json", request.url.encodedPath)
                return@MockEngine respond(
                    content = """{"data":[{"id":123}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }

            assertEquals(HttpMethod.Post, request.method)
            postRequestCount += 1
            assertEquals("/api/v2/orgs/companies/123/inventories.json", request.url.encodedPath)
            assertEquals(ContentType.Application.Json, request.body.contentType)

            val rawBody = (request.body as TextContent).text
            assertFalse(rawBody.contains("synthetic-test-token"))
            val json = Json.parseToJsonElement(rawBody).jsonObject
            assertEquals(setOf("title"), json.keys)
            assertEquals("  New inventory  ", json.getValue("title").jsonPrimitive.content)

            respond(
                content = """{"data":{"id":42}}""",
                status = successStatus,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine)
        val creator = KtorInventoryCreator(
            client = client,
            baseUrl = "https://web.zaico.co.jp/",
            token = "synthetic-test-token",
            companyRepository = CompanyRepository(
                client = client,
                baseUrl = "https://web.zaico.co.jp/",
                token = "synthetic-test-token",
                companiesPath = "/api/v2/orgs/companies.json"
            )
        )

        val result = creator.createInventory("  New inventory  ")

        assertEquals(CreateInventoryResult.Success(42L), result)
        assertEquals(2, requestCount)
        assertEquals(1, postRequestCount)
        creator.close()
    }

    @Test
    fun `200と201以外のHTTP応答_在庫作成がHTTP失敗になり再送しない`() = runBlocking {
        listOf(
            HttpStatusCode.NoContent,
            HttpStatusCode.BadRequest,
            HttpStatusCode.Unauthorized,
            HttpStatusCode.Forbidden,
            HttpStatusCode.InternalServerError
        )
            .forEach { status ->
                var requestCount = 0
                val creator = creator { requestCount += 1; respond("sensitive body", status) }

                val result = creator.createInventory("Valid")

                assertEquals(CreateInventoryResult.HttpFailure(status.value), result)
                assertEquals(1, requestCount)
                creator.close()
            }
    }

    @Test
    fun `200と201の不正応答_有効なdata_idがなくデコード失敗になる`() = runBlocking {
        listOf(HttpStatusCode.OK, HttpStatusCode.Created).forEach { status ->
            listOf(
                "not-json",
                "[]",
                """{"data":{}}""",
                """{"data":{"id":{"invalid":true}}}"""
            ).forEach { body ->
                val creator = creator {
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }

                assertEquals(CreateInventoryResult.DecodeFailure, creator.createInventory("Valid"))
                creator.close()
            }
        }
    }

    @Test
    fun `通信失敗_在庫作成がネットワーク失敗になり再送しない`() = runBlocking {
        var requestCount = 0
        val creator = creator {
            requestCount += 1
            throw IOException("synthetic transport failure")
        }

        assertEquals(CreateInventoryResult.NetworkFailure, creator.createInventory("Valid"))
        assertEquals(1, requestCount)
        creator.close()
    }

    @Test
    fun `キャンセル例外_在庫作成が例外を再送出する`() = runBlocking {
        val creator = creator { throw CancellationException("cancelled") }

        try {
            creator.createInventory("Valid")
            fail("CancellationException was not rethrown")
        } catch (_: CancellationException) {
            // Expected: structured cancellation is not converted into a UI failure.
        } finally {
            creator.close()
        }
    }

    @Test
    fun `トークン欠落_通信前に設定失敗になる`() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine { requestCount += 1; error("must not call network") })
        val creator = KtorInventoryCreator(
            client = client,
            baseUrl = "https://web.zaico.co.jp/",
            token = "   ",
            companyRepository = CompanyRepository(
                client, "https://web.zaico.co.jp/", "   ", "/api/v2/orgs/companies.json"
            )
        )

        assertEquals(CreateInventoryResult.ConfigurationFailure, creator.createInventory("Valid"))
        assertEquals(0, requestCount)
        creator.close()
    }

    @Test
    fun `HTTPS以外のベースURL_通信前に設定失敗になる`() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine { requestCount += 1; error("must not call network") })
        val creator = KtorInventoryCreator(
            client = client,
            baseUrl = "http://web.zaico.co.jp/",
            token = "synthetic-test-token",
            companyRepository = CompanyRepository(
                client, "http://web.zaico.co.jp/", "synthetic-test-token", "/api/v2/orgs/companies.json"
            )
        )

        assertEquals(CreateInventoryResult.ConfigurationFailure, creator.createInventory("Valid"))
        assertEquals(0, requestCount)
        creator.close()
    }

    private fun creator(
        handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData
    ): KtorInventoryCreator {
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(
                    content = """{"data":[{"id":123}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            } else {
                handler(request)
            }
        })
        return KtorInventoryCreator(
            client = client,
            baseUrl = "https://web.zaico.co.jp/",
            token = "synthetic-test-token",
            companyRepository = CompanyRepository(
                client, "https://web.zaico.co.jp/", "synthetic-test-token", "/api/v2/orgs/companies.json"
            )
        )
    }
}
