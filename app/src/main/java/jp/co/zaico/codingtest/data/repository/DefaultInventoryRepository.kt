package jp.co.zaico.codingtest.data.repository

import jp.co.zaico.codingtest.data.remote.InventoryRemoteService
import jp.co.zaico.codingtest.domain.company.CompanyRepository
import jp.co.zaico.codingtest.domain.inventory.Inventory
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 拠点IDを解決して在庫一覧・詳細Remoteへ委譲するRepository実装。
 *
 * 旧クラス名: `KtorInventoryRepository`
 */
@Singleton
class DefaultInventoryRepository @Inject constructor(
    private val companyRepository: CompanyRepository,
    private val remote: InventoryRemoteService
) : InventoryRepository {

    override suspend fun getInventories(): List<Inventory> =
        remote.getInventories(companyRepository.requireCompanyId())

    override suspend fun getInventory(inventoryId: Int): Inventory =
        remote.getInventory(companyRepository.requireCompanyId(), inventoryId)
}
