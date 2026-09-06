package jp.co.zaico.codingtest.domain.inventory

import jp.co.zaico.codingtest.domain.result.CreateInventoryResult

/**
 * 在庫作成を実行するDomain契約。
 */
interface InventoryCreator {
    suspend fun createInventory(title: String): CreateInventoryResult
}
