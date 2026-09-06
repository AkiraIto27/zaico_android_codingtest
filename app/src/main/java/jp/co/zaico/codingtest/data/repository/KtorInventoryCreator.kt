package jp.co.zaico.codingtest.data.repository

import io.ktor.client.HttpClient
import jp.co.zaico.codingtest.data.remote.InventoryCreateRemoteResult
import jp.co.zaico.codingtest.data.remote.InventoryCreateRemoteService
import kotlinx.coroutines.CancellationException

internal class KtorInventoryCreator(
    private val client: HttpClient,
    private val token: String,
    private val companyRepository: CompanyRepository,
    private val remote: InventoryCreateRemoteService
) : InventoryCreator {
    override suspend fun createInventory(title: String): CreateInventoryResult {
        if (token.isBlank()) {
            return CreateInventoryResult.ConfigurationFailure
        }
        val companyId = when (val companyResult = companyRepository.getCompanyId()) {
            is CompanyIdResult.Success -> companyResult.companyId
            CompanyIdResult.Empty -> return CreateInventoryResult.EmptyCompany
            CompanyIdResult.ConfigurationFailure -> return CreateInventoryResult.ConfigurationFailure
            is CompanyIdResult.HttpFailure -> return CreateInventoryResult.HttpFailure(companyResult.statusCode)
            CompanyIdResult.DecodeFailure -> return CreateInventoryResult.DecodeFailure
            CompanyIdResult.NetworkFailure -> return CreateInventoryResult.NetworkFailure
        }
        return try {
            when (val result = remote.createInventory(companyId, title)) {
                is InventoryCreateRemoteResult.Success -> CreateInventoryResult.Success(result.inventoryId)
                is InventoryCreateRemoteResult.HttpFailure -> CreateInventoryResult.HttpFailure(result.statusCode)
                InventoryCreateRemoteResult.DecodeFailure -> CreateInventoryResult.DecodeFailure
                InventoryCreateRemoteResult.NetworkFailure -> CreateInventoryResult.NetworkFailure
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }

    override fun close() {
        client.close()
    }
}
