package jp.co.zaico.codingtest.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class InventoryRemoteServiceTest {

    @Test
    fun 一覧の3種類のルート形式_Inventoryへ変換する() = runTest {
        val cases = listOf(
            """[{"id":1,"title":"Array","quantity":"1"}]""",
            """{"data":[{"id":2,"title":"Data array","quantity":"2"}]}""",
            """{"id":3,"title":"Single object","quantity":"3"}"""
        )

        cases.forEach { body ->
            val client = HttpClient(MockEngine { request ->
                assertEquals(URLProtocol.HTTPS, request.url.protocol)
                assertEquals(
                    "/api/v2/orgs/companies/123/inventories.json",
                    request.url.encodedPath
                )
                assertEquals("Bearer synthetic-test-token", request.headers[HttpHeaders.Authorization])
                respond(content = body, status = HttpStatusCode.OK, headers = jsonHeaders)
            })
            try {
                val result = KtorInventoryRemoteService(
                    client = client,
                    baseUrl = "https://web.zaico.co.jp/",
                    token = "synthetic-test-token"
                ).getInventories(companyId = 123)

                assertEquals(1, result.size)
                assertEquals(body.substringAfter("\"id\":").substringBefore(",").toInt(), result.single().id)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun 詳細のdataオブジェクト_Inventoryへ変換する() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(
                "/api/v2/orgs/companies/123/inventories/77.json",
                request.url.encodedPath
            )
            assertEquals("Bearer synthetic-test-token", request.headers[HttpHeaders.Authorization])
            respond(
                content = """{"data":{"id":77,"title":"Shelf","quantity":null}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        })

        try {
            val result = KtorInventoryRemoteService(
                client = client,
                baseUrl = "https://web.zaico.co.jp",
                token = "synthetic-test-token"
            ).getInventory(companyId = 123, inventoryId = 77)

            assertEquals(77, result.id)
            assertEquals("Shelf", result.title)
            assertEquals("", result.quantity)
        } finally {
            client.close()
        }
    }

    @Test
    fun 一覧と詳細のHTTPエラーまたは不正JSON_例外を呼び出し元へ返す() = runTest {
        listOf(
            HttpStatusCode.BadRequest to "ignored",
            HttpStatusCode.NoContent to "ignored",
            HttpStatusCode.OK to "not-json"
        ).forEach { (status, body) ->
            val client = HttpClient(MockEngine {
                respond(content = body, status = status, headers = jsonHeaders)
            })
            try {
                val service = KtorInventoryRemoteService(
                    client = client,
                    baseUrl = "https://web.zaico.co.jp",
                    token = "synthetic-test-token"
                )
                assertThrows<Exception> { service.getInventories(123) }
                assertThrows<Exception> { service.getInventory(123, 77) }
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun 通信失敗の場合_例外を呼び出し元へ返す() = runTest {
        val client = HttpClient(MockEngine {
            throw IOException("synthetic transport failure")
        })

        try {
            val service = KtorInventoryRemoteService(
                client = client,
                baseUrl = "https://web.zaico.co.jp",
                token = "synthetic-test-token"
            )
            assertThrows<IOException> { service.getInventories(123) }
            assertThrows<IOException> { service.getInventory(123, 77) }
        } finally {
            client.close()
        }
    }

    @Test
    fun キャンセル例外の場合_CancellationExceptionを再送出する() = runTest {
        val client = HttpClient(MockEngine {
            throw CancellationException("synthetic cancellation")
        })

        try {
            val service = KtorInventoryRemoteService(
                client = client,
                baseUrl = "https://web.zaico.co.jp",
                token = "synthetic-test-token"
            )
            assertThrows<CancellationException> { service.getInventories(123) }
            assertThrows<CancellationException> { service.getInventory(123, 77) }
        } finally {
            client.close()
        }
    }

    private companion object {
        val jsonHeaders = headersOf(
            HttpHeaders.ContentType,
            ContentType.Application.Json.toString()
        )
    }

    private suspend inline fun <reified T : Throwable> assertThrows(
        crossinline block: suspend () -> Unit
    ) {
        try {
            block()
            fail("${T::class.simpleName} was not thrown")
        } catch (exception: Throwable) {
            if (exception !is T) throw exception
        }
    }
}
