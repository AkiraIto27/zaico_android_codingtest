package jp.co.zaico.codingtest

import android.content.Context
import androidx.lifecycle.ViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal fun normalizeInventoryListRoot(root: JsonElement): JsonArray = when (root) {
    is JsonArray -> root
    is JsonObject -> root["data"]?.let { data ->
        data as? JsonArray ?: throw IllegalArgumentException("Inventory list data is not an array")
    } ?: JsonArray(listOf(root))
    else -> throw IllegalArgumentException("Inventory list response has an unsupported root")
}

class FirstViewModel(
    private val context: Context,
    private val companyRepository: CompanyRepository
) : ViewModel() {

    suspend fun getInventories(): List<Inventory> {
        val companyId = companyRepository.requireCompanyId()
        val client = HttpClient(Android)
        return try {
            val response = client.get(
                "${context.getString(R.string.api_endpoint).trimEnd('/')}/api/v2/orgs/companies/$companyId/inventories.json"
            ) {
                header(HttpHeaders.Authorization, "Bearer ${context.getString(R.string.api_token)}")
            }
            check(response.status == HttpStatusCode.OK) { "Inventory list request failed" }
            normalizeInventoryListRoot(Json.parseToJsonElement(response.bodyAsText()))
                .map { it.jsonObject.toInventory() }
        } finally {
            client.close()
        }
    }
}
