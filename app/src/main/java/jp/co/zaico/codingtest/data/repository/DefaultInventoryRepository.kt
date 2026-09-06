package jp.co.zaico.codingtest.data.repository

import jp.co.zaico.codingtest.data.remote.InventoryRemoteService
import jp.co.zaico.codingtest.domain.company.CompanyRepository
import jp.co.zaico.codingtest.domain.inventory.Inventory
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会社IDを解決して在庫取得Remoteへ委譲するRepository実装。
 *
 * 旧クラス名: `KtorInventoryRepository`
 */
@Singleton
class DefaultInventoryRepository @Inject internal constructor(
    private val companyRepository: CompanyRepository,
    private val remote: InventoryRemoteService
) : InventoryRepository {

    override suspend fun getInventories(): List<Inventory> {
        return remote.getInventories(companyRepository.requireCompanyId())
    }

    override suspend fun getInventory(inventoryId: Int): Inventory {
        return remote.getInventory(companyRepository.requireCompanyId(), inventoryId)
    }
}
