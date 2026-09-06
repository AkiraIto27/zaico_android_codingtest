package jp.co.zaico.codingtest.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import jp.co.zaico.codingtest.domain.inventory.Inventory
import jp.co.zaico.codingtest.data.remote.CompanyRemoteResult
import jp.co.zaico.codingtest.data.remote.CompanyRemoteService
import jp.co.zaico.codingtest.data.remote.InventoryRemoteService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import kotlinx.coroutines.isActive
import org.junit.Test
import java.io.IOException

class InventoryRepositoryBoundaryTest {

    @Test
    fun 一覧取得の場合_会社IDをRemoteへ渡しclientを閉じる() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote()
        val repository = repository(client, remote)

        assertEquals(listOf(Inventory(1, "Desk", "3")), repository.getInventories())
        assertEquals(listOf(123), remote.listCompanyIds)
        assertFalse(client.coroutineContext.isActive)
    }

    @Test
    fun 詳細取得が成功した場合_会社IDと在庫IDをRemoteへ渡しclientを閉じる() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote()
        val repository = repository(client, remote)

        assertEquals(Inventory(77, "Shelf", "1"), repository.getInventory(77))
        assertEquals(listOf(123), remote.detailCompanyIds)
        assertEquals(listOf(77), remote.detailInventoryIds)
        assertFalse(client.coroutineContext.isActive)
    }

    @Test
    fun 一覧取得でRemoteが通信失敗した場合_会社IDを渡しclientを閉じて例外を返す() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote(listFailure = IOException("synthetic failure"))
        val repository = repository(client, remote)

        assertThrows<IOException> { repository.getInventories() }
        assertEquals(listOf(123), remote.listCompanyIds)
        assertFalse(client.coroutineContext.isActive)
    }

    @Test
    fun 詳細取得でRemoteが通信失敗した場合_clientを閉じて例外を返す() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote(detailFailure = IOException("synthetic failure"))
        val repository = repository(client, remote)

        assertThrows<IOException> { repository.getInventory(77) }
        assertEquals(listOf(123), remote.detailCompanyIds)
        assertEquals(listOf(77), remote.detailInventoryIds)
        assertFalse(client.coroutineContext.isActive)
    }

    @Test
    fun Remoteがキャンセルした場合_CancellationExceptionを再送出しclientを閉じる() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote(detailFailure = CancellationException("cancelled"))
        val repository = repository(client, remote)

        assertThrows<CancellationException> { repository.getInventory(77) }
        assertEquals(listOf(123), remote.detailCompanyIds)
        assertEquals(listOf(77), remote.detailInventoryIds)
        assertFalse(client.coroutineContext.isActive)
    }

    @Test
    fun 一覧取得でRemoteがキャンセルした場合_CancellationExceptionを再送出しclientを閉じる() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote(listFailure = CancellationException("cancelled"))
        val repository = repository(client, remote)

        assertThrows<CancellationException> { repository.getInventories() }
        assertEquals(listOf(123), remote.listCompanyIds)
        assertFalse(client.coroutineContext.isActive)
    }

    @Test
    fun 会社取得が失敗した場合_Remoteを呼ばずCompanyRepositoryExceptionを返す() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote()
        val companyRemote = object : CompanyRemoteService {
            override suspend fun getCompanies(): CompanyRemoteResult =
                CompanyRemoteResult.HttpFailure(HttpStatusCode.ServiceUnavailable.value)
        }
        val repository = KtorInventoryRepository(
            companyRepository = CompanyRepository(companyRemote, token = "synthetic-test-token"),
            clientFactory = { client },
            remoteFactory = { remote }
        )

        val exception = assertThrows<CompanyRepositoryException> {
            repository.getInventories()
        }
        assertEquals(CompanyIdResult.HttpFailure(503), exception.result)
        assertTrue(remote.listCompanyIds.isEmpty())
        assertTrue(client.coroutineContext.isActive)
        client.close()
    }

    private fun repository(
        client: HttpClient,
        remote: RecordingInventoryRemote
    ): KtorInventoryRepository = KtorInventoryRepository(
        companyRepository = CompanyRepository(
            remote = object : CompanyRemoteService {
                override suspend fun getCompanies(): CompanyRemoteResult =
                    CompanyRemoteResult.Success(
                        companies = listOf(jp.co.zaico.codingtest.data.remote.dto.CompanyRemoteCompany(123))
                    )
            },
            token = "synthetic-test-token"
        ),
        clientFactory = { client },
        remoteFactory = { remote }
    )

    private fun testClient(): HttpClient = HttpClient(MockEngine {
        respond(content = "unused", status = HttpStatusCode.OK)
    })

    private class RecordingInventoryRemote(
        private val listFailure: Throwable? = null,
        private val detailFailure: Throwable? = null
    ) : InventoryRemoteService {
        val listCompanyIds = mutableListOf<Int>()
        val detailCompanyIds = mutableListOf<Int>()
        val detailInventoryIds = mutableListOf<Int>()

        override suspend fun getInventories(companyId: Int): List<Inventory> {
            listCompanyIds += companyId
            listFailure?.let { throw it }
            return listOf(Inventory(1, "Desk", "3"))
        }

        override suspend fun getInventory(companyId: Int, inventoryId: Int): Inventory {
            detailCompanyIds += companyId
            detailInventoryIds += inventoryId
            detailFailure?.let { throw it }
            return Inventory(inventoryId, "Shelf", "1")
        }
    }

    private suspend inline fun <reified T : Throwable> assertThrows(
        crossinline block: suspend () -> Unit
    ): T {
        try {
            block()
            fail("${T::class.simpleName} was not thrown")
        } catch (exception: Throwable) {
            if (exception is T) return exception
            throw AssertionError("Expected ${T::class.simpleName}, got ${exception::class.simpleName}", exception)
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }
}
