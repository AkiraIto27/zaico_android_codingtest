package jp.co.zaico.codingtest

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class SecondViewModel(
    val context: Context
) : ViewModel() {

    // データ取得
    @SuppressLint("DefaultLocale")
    fun getInventory(inventoryId: Int): Inventory = runBlocking {

        val client = HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        return@runBlocking GlobalScope.async {
            val response: HttpResponse = client!!.get(
                String.format(
                    "%s/api/v1/inventories/%d",
                    context.getString(R.string.api_endpoint),
                    inventoryId
                )
            ) {
                header("Authorization", String.format("Bearer %s", context.getString(R.string.api_token)))
            }

            val jsonText = response.bodyAsText()
            val json = Json.parseToJsonElement(jsonText).jsonObject

            return@async Inventory(
                id = json["id"].toString().replace(""""""", "").toInt(),
                title = json["title"].toString().replace(""""""", ""),
                quantity = json["quantity"].toString().replace(""""""", "")
            )

        }.await()

    }

}