package jp.co.zaico.codingtest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class InventoryQuantityMappingTest {

    @Test
    fun quantityの各JSON形状_仕様どおり文字列化または解析エラーになる() {
        assertEquals(
            "",
            inventoryFrom("""{"id":1,"title":"Null quantity","quantity":null}""").quantity
        )
        assertEquals(
            "",
            inventoryFrom("""{"id":2,"title":"Missing quantity"}""").quantity
        )
        assertEquals(
            "12.5",
            inventoryFrom("""{"id":3,"title":"Decimal quantity","quantity":"12.5"}""").quantity
        )
        assertEquals(
            "0",
            inventoryFrom("""{"id":4,"title":"Zero quantity","quantity":"0"}""").quantity
        )

        try {
            inventoryFrom("""{"id":5,"title":"Object quantity","quantity":{"value":1}}""")
            fail("An object quantity must not be converted to an empty string")
        } catch (_: IllegalArgumentException) {
            // A non-primitive quantity is malformed input.
        }
    }

    private fun inventoryFrom(raw: String): Inventory =
        Json.parseToJsonElement(raw).jsonObject.toInventory()
}
