package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InventoryListResponseTest {

    @Test
    fun 一覧のHTTP200応答が配列の場合_在庫一覧へ変換する() {
        val result = decodeInventoryListResponse(
            statusCode = 200,
            rawBody = """
                [
                  {"id":1,"title":"First","quantity":"2"},
                  {"id":2,"title":"Second","quantity":"3.5"}
                ]
            """.trimIndent()
        )

        assertEquals(
            InventoryListResult.Success(
                listOf(
                    Inventory(id = 1, title = "First", quantity = "2"),
                    Inventory(id = 2, title = "Second", quantity = "3.5")
                )
            ),
            result
        )
    }

    @Test
    fun 一覧のHTTP200応答が空配列の場合_空の一覧を返す() {
        assertEquals(
            InventoryListResult.Success(emptyList()),
            decodeInventoryListResponse(statusCode = 200, rawBody = "[]")
        )
    }

    @Test
    fun 一覧応答がHTTP200以外の場合_JSONを解析せずHTTPエラーとして扱う() {
        assertEquals(
            InventoryListResult.HttpFailure(401),
            decodeInventoryListResponse(
                statusCode = 401,
                rawBody = """{"code":401,"status":"error","message":"synthetic"}"""
            )
        )
    }

    @Test
    fun 一覧のHTTP200応答が単一オブジェクトの場合_要素が1件の一覧へ変換する() {
        assertEquals(
            InventoryListResult.Success(
                listOf(Inventory(id = 3, title = "Single", quantity = "8"))
            ),
            decodeInventoryListResponse(
                statusCode = 200,
                rawBody = """{"id":3,"title":"Single","quantity":"8","unit":"box"}"""
            )
        )
    }

    @Test
    fun 一覧のHTTP200応答がエラーオブジェクトの場合_例外を投げずデコードエラーとして扱う() {
        assertEquals(
            InventoryListResult.DecodeFailure,
            decodeInventoryListResponse(
                statusCode = 200,
                rawBody = """{"code":401,"status":"error","message":"synthetic"}"""
            )
        )
    }

    @Test
    fun 一覧応答のJSONや在庫データが不正な場合_デコードエラーとして扱う() {
        listOf(
            "{broken",
            """[{"title":"Missing id","quantity":"1"}]""",
            """[{"id":"not-a-number","title":"Invalid id","quantity":"1"}]"""
        ).forEach { rawBody ->
            assertEquals(
                InventoryListResult.DecodeFailure,
                decodeInventoryListResponse(statusCode = 200, rawBody = rawBody)
            )
        }
    }

    @Test
    fun 一覧を取得した場合_認証付きAPI_v1のGETで配列の在庫データを取得する() = runBlocking {
        var requestCount = 0
        val loader = KtorInventoryListLoader(
            client = HttpClient(MockEngine { request ->
                requestCount += 1
                assertEquals(HttpMethod.Get, request.method)
                assertEquals(URLProtocol.HTTPS, request.url.protocol)
                assertEquals("web.zaico.co.jp", request.url.host)
                assertEquals("/api/v1/inventories", request.url.encodedPath)
                assertFalse(request.url.encodedPath.contains("/api/v2/"))
                assertEquals(
                    "Bearer synthetic-test-token",
                    request.headers[HttpHeaders.Authorization]
                )
                assertFalse(request.url.toString().contains("synthetic-test-token"))

                respond(
                    content = """[{"id":7,"title":"Loaded","quantity":"4"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString()
                    )
                )
            }),
            baseUrl = "https://web.zaico.co.jp/",
            token = "synthetic-test-token"
        )

        try {
            assertEquals(
                InventoryListResult.Success(
                    listOf(Inventory(id = 7, title = "Loaded", quantity = "4"))
                ),
                loader.load()
            )
            assertEquals(1, requestCount)
        } finally {
            loader.close()
        }
    }

    @Test
    fun 一覧取得でHTTPエラーを受け取った場合_解析例外を出さずHTTPエラーとして扱う() = runBlocking {
        val loader = KtorInventoryListLoader(
            client = HttpClient(MockEngine {
                respond(
                    content = """{"code":401,"status":"error","message":"synthetic"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString()
                    )
                )
            }),
            baseUrl = "https://web.zaico.co.jp/",
            token = "synthetic-test-token"
        )

        try {
            assertEquals(InventoryListResult.HttpFailure(401), loader.load())
        } finally {
            loader.close()
        }
    }

    @Test
    fun 一覧取得で単一オブジェクトを受け取った場合_要素が1件の一覧を返す() = runBlocking {
        val loader = KtorInventoryListLoader(
            client = HttpClient(MockEngine {
                respond(
                    content = """{"id":9,"title":"Single over HTTP","quantity":"6","unit":"box"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString()
                    )
                )
            }),
            baseUrl = "https://web.zaico.co.jp/",
            token = "synthetic-test-token"
        )

        try {
            assertEquals(
                InventoryListResult.Success(
                    listOf(Inventory(id = 9, title = "Single over HTTP", quantity = "6"))
                ),
                loader.load()
            )
        } finally {
            loader.close()
        }
    }

    @Test
    fun 一覧取得でHTTP200のエラーオブジェクトを受け取った場合_デコードエラーとして扱う() = runBlocking {
        val loader = KtorInventoryListLoader(
            client = HttpClient(MockEngine {
                respond(
                    content = """{"code":401,"status":"error","message":"synthetic"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString()
                    )
                )
            }),
            baseUrl = "https://web.zaico.co.jp/",
            token = "synthetic-test-token"
        )

        try {
            assertEquals(InventoryListResult.DecodeFailure, loader.load())
        } finally {
            loader.close()
        }
    }

    @Test
    fun 一覧取得用トークンが未設定の場合_通信せず設定エラーとして扱う() = runBlocking {
        var requestCount = 0
        val loader = KtorInventoryListLoader(
            client = HttpClient(MockEngine {
                requestCount += 1
                error("network must not be called")
            }),
            baseUrl = "https://web.zaico.co.jp/",
            token = "   "
        )

        try {
            assertEquals(InventoryListResult.ConfigurationFailure, loader.load())
            assertEquals(0, requestCount)
        } finally {
            loader.close()
        }
    }
}
