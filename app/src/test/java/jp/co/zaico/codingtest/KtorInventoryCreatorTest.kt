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
    @Acceptance("AC-002", "AC-004", "AC-006")
    fun createInventory_postsTheExactV2ContractAndDecodesDataId() = runBlocking {
        var requestCount = 0
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
            assertEquals("/api/v2/orgs/companies/123/inventories.json", request.url.encodedPath)
            assertEquals(ContentType.Application.Json, request.body.contentType)

            val rawBody = (request.body as TextContent).text
            assertFalse(rawBody.contains("synthetic-test-token"))
            val json = Json.parseToJsonElement(rawBody).jsonObject
            assertEquals(setOf("title"), json.keys)
            assertEquals("  New inventory  ", json.getValue("title").jsonPrimitive.content)

            respond(
                content = """{"data":{"id":42}}""",
                status = HttpStatusCode.Created,
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
        creator.close()
    }

    @Test
    @Acceptance("AC-005")
    fun createInventory_mapsDocumentedAndUnexpectedHttpErrorsWithoutRetrying() = runBlocking {
        listOf(HttpStatusCode.BadRequest, HttpStatusCode.NotAcceptable, HttpStatusCode.InternalServerError)
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
    @Acceptance("AC-004", "AC-005")
    fun createInventory_rejectsMalformedHttp200AsDecodeFailure() = runBlocking {
        listOf(
            "not-json",
            "[]",
            """{"data":{}}""",
            """{"data":{"id":{"invalid":true}}}"""
        ).forEach { body ->
            val creator = creator {
                respond(
                    content = body,
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }

            assertEquals(CreateInventoryResult.DecodeFailure, creator.createInventory("Valid"))
            creator.close()
        }
    }

    @Test
    @Acceptance("AC-005")
    fun createInventory_mapsTransportFailureWithoutRetrying() = runBlocking {
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
    @Acceptance("AC-005")
    fun createInventory_rethrowsCancellation() = runBlocking {
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
    @Acceptance("AC-006")
    fun createInventory_withMissingTokenFailsBeforeNetworking() = runBlocking {
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
    @Acceptance("AC-006")
    fun createInventory_withNonHttpsBaseUrlFailsBeforeNetworking() = runBlocking {
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
