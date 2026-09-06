package jp.co.zaico.codingtest.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import jp.co.zaico.codingtest.data.remote.CompanyRemoteResult
import jp.co.zaico.codingtest.data.remote.CompanyRemoteService
import jp.co.zaico.codingtest.data.remote.InventoryRemoteService
import jp.co.zaico.codingtest.data.remote.dto.CompanyRemoteCompany
import jp.co.zaico.codingtest.domain.company.CompanyIdResult
import jp.co.zaico.codingtest.domain.company.CompanyRepositoryException
import jp.co.zaico.codingtest.domain.inventory.Inventory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class InventoryRepositoryBoundaryTest {

    @Test
    fun 一覧取得の場合_会社IDをRemoteへ渡し共有clientを閉じない() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote(client)
        val repository = repository(remote)

        assertEquals(listOf(Inventory(1, "Desk", "3")), repository.getInventories())
        assertEquals(listOf(123), remote.listCompanyIds)
        assertTrue(client.coroutineContext.isActive)
        client.close()
    }

    @Test
    fun 詳細取得が成功した場合_会社IDと在庫IDをRemoteへ渡し共有clientを閉じない() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote(client)
        val repository = repository(remote)

        assertEquals(Inventory(77, "Shelf", "1"), repository.getInventory(77))
        assertEquals(listOf(123), remote.detailCompanyIds)
        assertEquals(listOf(77), remote.detailInventoryIds)
        assertTrue(client.coroutineContext.isActive)
        client.close()
    }

    @Test
    fun 一覧取得でRemoteが通信失敗した場合_会社IDを渡し共有clientを閉じない() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote(client, listFailure = IOException("synthetic failure"))
        val repository = repository(remote)

        assertThrows<IOException> { repository.getInventories() }
        assertEquals(listOf(123), remote.listCompanyIds)
        assertTrue(client.coroutineContext.isActive)
        client.close()
    }

    @Test
    fun 詳細取得でRemoteが通信失敗した場合_共有clientを閉じない() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote(client, detailFailure = IOException("synthetic failure"))
        val repository = repository(remote)

        assertThrows<IOException> { repository.getInventory(77) }
        assertEquals(listOf(123), remote.detailCompanyIds)
        assertEquals(listOf(77), remote.detailInventoryIds)
        assertTrue(client.coroutineContext.isActive)
        client.close()
    }

    @Test
    fun Remoteがキャンセルした場合_CancellationExceptionを再送出し共有clientを閉じない() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote(client, detailFailure = CancellationException("cancelled"))
        val repository = repository(remote)

        assertThrows<CancellationException> { repository.getInventory(77) }
        assertEquals(listOf(123), remote.detailCompanyIds)
        assertEquals(listOf(77), remote.detailInventoryIds)
        assertTrue(client.coroutineContext.isActive)
        client.close()
    }

    @Test
    fun 一覧取得でRemoteがキャンセルした場合_CancellationExceptionを再送出し共有clientを閉じない() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote(client, listFailure = CancellationException("cancelled"))
        val repository = repository(remote)

        assertThrows<CancellationException> { repository.getInventories() }
        assertEquals(listOf(123), remote.listCompanyIds)
        assertTrue(client.coroutineContext.isActive)
        client.close()
    }

    @Test
    fun 会社取得が失敗した場合_Remoteを呼ばずCompanyRepositoryExceptionを返す() = runTest {
        val client = testClient()
        val remote = RecordingInventoryRemote(client)
        val companyRemote = object : CompanyRemoteService {
            override suspend fun getCompanies(): CompanyRemoteResult =
                CompanyRemoteResult.HttpFailure(HttpStatusCode.ServiceUnavailable.value)
        }
        val repository = DefaultInventoryRepository(
            companyRepository = DefaultCompanyRepository(
                remote = companyRemote,
                token = "synthetic-test-token"
            ),
            remote = remote
        )

        val exception = assertThrows<CompanyRepositoryException> {
            repository.getInventories()
        }
        assertEquals(CompanyIdResult.HttpFailure(503), exception.result)
        assertTrue(remote.listCompanyIds.isEmpty())
        assertTrue(client.coroutineContext.isActive)
        client.close()
    }

    private fun repository(remote: RecordingInventoryRemote): DefaultInventoryRepository =
        DefaultInventoryRepository(
            companyRepository = DefaultCompanyRepository(
                remote = object : CompanyRemoteService {
                    override suspend fun getCompanies(): CompanyRemoteResult =
                        CompanyRemoteResult.Success(listOf(CompanyRemoteCompany(123)))
                },
                token = "synthetic-test-token"
            ),
            remote = remote
        )

    private fun testClient(): HttpClient = HttpClient(MockEngine {
        respond(content = "unused", status = HttpStatusCode.OK)
    })

    private class RecordingInventoryRemote(
        val client: HttpClient,
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
