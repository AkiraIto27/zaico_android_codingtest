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
    fun 作成リクエストを送信した場合_API_v1の仕様に従って送信し在庫IDを取得する() = runBlocking {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount += 1
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(URLProtocol.HTTPS, request.url.protocol)
            assertEquals("web.zaico.co.jp", request.url.host)
            assertEquals("/api/v1/inventories", request.url.encodedPath)
            assertFalse(request.url.encodedPath.contains("/api/v2/"))
            assertTrue(request.url.parameters.isEmpty())
            assertEquals("Bearer synthetic-test-token", request.headers[HttpHeaders.Authorization])
            assertEquals(ContentType.Application.Json, request.body.contentType)
            assertFalse(request.url.toString().contains("synthetic-test-token"))

            val rawBody = (request.body as TextContent).text
            assertFalse(rawBody.contains("synthetic-test-token"))
            val json = Json.parseToJsonElement(rawBody).jsonObject
            assertEquals(setOf("title"), json.keys)
            assertEquals("  New inventory  ", json.getValue("title").jsonPrimitive.content)

            respond(
                content = """{"code":200,"status":"success","message":"created","data_id":42}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val creator = KtorInventoryCreator(
            client = HttpClient(engine),
            baseUrl = "https://web.zaico.co.jp/",
            token = "synthetic-test-token"
        )

        val result = creator.createInventory("  New inventory  ")

        assertEquals(CreateInventoryResult.Success(42L), result)
        assertEquals(1, requestCount)
        creator.close()
    }

    @Test
    fun 作成APIがHTTPエラーを返した場合_再送せずHTTPエラーとして扱う() = runBlocking {
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
    fun 作成APIがHTTP200で不正な応答を返した場合_デコードエラーとして扱う() = runBlocking {
        listOf(
            "not-json",
            "[]",
            """{"code":200,"status":"success"}""",
            """{"code":200,"data_id":{"invalid":true}}"""
        ).forEach { body ->
            val creator = creator {
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }

            assertEquals(CreateInventoryResult.DecodeFailure, creator.createInventory("Valid"))
            creator.close()
        }
    }

    @Test
    fun 作成APIへの通信に失敗した場合_再送せず通信エラーとして扱う() = runBlocking {
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
    fun 作成リクエストがキャンセルされた場合_キャンセル例外を再送出する() = runBlocking {
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
    fun 作成用トークンが未設定の場合_通信せず設定エラーとして扱う() = runBlocking {
        var requestCount = 0
        val creator = KtorInventoryCreator(
            client = HttpClient(MockEngine { requestCount += 1; error("must not call network") }),
            baseUrl = "https://web.zaico.co.jp/",
            token = "   "
        )

        assertEquals(CreateInventoryResult.ConfigurationFailure, creator.createInventory("Valid"))
        assertEquals(0, requestCount)
        creator.close()
    }

    private fun creator(
        handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData
    ): KtorInventoryCreator = KtorInventoryCreator(
        client = HttpClient(MockEngine(handler)),
        baseUrl = "https://web.zaico.co.jp/",
        token = "synthetic-test-token"
    )
}
