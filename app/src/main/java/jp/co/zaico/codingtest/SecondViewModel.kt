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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SecondViewModel(
    private val context: Context,
    private val companyRepository: CompanyRepository
) : ViewModel() {
    suspend fun getInventory(inventoryId: Int): Inventory {
        val companyId = companyRepository.requireCompanyId()
        val client = HttpClient(Android)
        return try {
            val response = client.get(
                "${context.getString(R.string.api_endpoint).trimEnd('/')}/api/v2/orgs/companies/$companyId/inventories/$inventoryId.json"
            ) {
                header(HttpHeaders.Authorization, "Bearer ${context.getString(R.string.api_token)}")
            }
            check(response.status == HttpStatusCode.OK) { "Inventory detail request failed" }
            val data = Json.parseToJsonElement(response.bodyAsText())
                .jsonObject.getValue("data").jsonObject
            Inventory(
                id = data.getValue("id").jsonPrimitive.int,
                title = data["title"]?.jsonPrimitive?.content.orEmpty(),
                quantity = data["quantity"]?.jsonPrimitive?.content.orEmpty()
            )
        } finally {
            client.close()
        }
    }
}
