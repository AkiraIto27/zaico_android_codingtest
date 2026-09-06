package jp.co.zaico.codingtest.data.repository

import jp.co.zaico.codingtest.data.remote.InventoryCreateRemoteResult
import jp.co.zaico.codingtest.data.remote.InventoryCreateRemoteService
import jp.co.zaico.codingtest.di.ApiToken
import jp.co.zaico.codingtest.domain.company.CompanyIdResult
import jp.co.zaico.codingtest.domain.company.CompanyRepository
import jp.co.zaico.codingtest.domain.inventory.InventoryCreator
import jp.co.zaico.codingtest.domain.result.CreateInventoryResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 拠点IDを解決して在庫作成Remoteへ委譲するRepository実装。
 *
 * 旧クラス名: `KtorInventoryCreator`
 */
@Singleton
class DefaultInventoryCreator @Inject constructor(
    @ApiToken private val token: String,
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
}
