package jp.co.zaico.codingtest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class InventoryListResponseTest {

    @Test
    fun 一覧応答のJSONルートが単一オブジェクトの場合_要素1件の配列へ正規化する() {
        val root = Json.parseToJsonElement("""{"id":1,"title":"First"}""")

        assertEquals(JsonArray(listOf(root)), normalizeInventoryListRoot(root))
    }

    @Test
    fun 一覧応答のJSONルートが配列の場合_配列をそのまま返す() {
        val root = Json.parseToJsonElement("""[{"id":1},{"id":2}]""")

        assertEquals(root, normalizeInventoryListRoot(root))
    }

    @Test
    fun 一覧応答のJSONルートが空配列の場合_空配列をそのまま返す() {
        val root = Json.parseToJsonElement("[]")

        assertEquals(JsonArray(emptyList()), normalizeInventoryListRoot(root))
    }

    @Test
    fun 一覧応答のJSONルートがオブジェクトでも配列でもない場合_例外として扱う() {
        val root = Json.parseToJsonElement("\"synthetic-raw-payload\"")

        val error = assertThrows(IllegalArgumentException::class.java) {
            normalizeInventoryListRoot(root)
        }

        assertFalse(error.message.orEmpty().contains("synthetic-raw-payload"))
    }
}
