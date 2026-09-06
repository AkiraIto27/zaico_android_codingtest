package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class CompanyRepositoryTest {

    @Test
    fun 正常な拠点一覧に追加フィールドがある場合_先頭の拠点IDを取得する() = runTest {
        val fixture = fixture {
            respond(
                content = """{"data":[{"id":123,"name":"Main","ignored":{"value":true}},{"id":999}]}""",
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
        val fixture = fixture {
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
        val fixture = fixture {
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
        val fixture = fixture {
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
        val fixture = fixture {
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
        val fixture = fixture {
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
        val fixture = fixture {
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
        val fixture = fixture {
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
        val fixture = fixture {
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
        val fixture = fixture {
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
        val repository = CompanyRepository(
            client = client,
            baseUrl = "https://web.zaico.co.jp/",
            token = "synthetic-test-token",
            companiesPath = "/api/v2/orgs/companies.json"
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
        val repository: CompanyRepository,
        val client: HttpClient,
        val requestCount: AtomicInteger
    )

    private fun fixture(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): Fixture {
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
        return Fixture(
            repository = CompanyRepository(
                client = client,
                baseUrl = "https://web.zaico.co.jp/",
                token = "synthetic-test-token",
                companiesPath = "/api/v2/orgs/companies.json"
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
