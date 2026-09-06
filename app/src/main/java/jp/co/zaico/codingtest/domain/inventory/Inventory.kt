package jp.co.zaico.codingtest.domain.inventory

/**
 * 在庫を表すDomainモデル。
 */
data class Inventory(
    val id: Int,
    val title: String,
    val quantity: String
)
