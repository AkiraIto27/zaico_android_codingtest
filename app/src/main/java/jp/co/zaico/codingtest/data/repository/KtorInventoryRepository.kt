package jp.co.zaico.codingtest.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import jp.co.zaico.codingtest.data.remote.InventoryRemoteService
import jp.co.zaico.codingtest.data.remote.KtorInventoryRemoteService
import jp.co.zaico.codingtest.domain.inventory.Inventory
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository
import kotlinx.serialization.json.Json

class KtorInventoryRepository private constructor(
    private val baseUrl: String,
    private val token: String,
    private val companyRepository: CompanyRepository,
    private val json: Json,
    private val clientFactory: () -> HttpClient,
    private val remoteFactory: (HttpClient, String, String, Json) -> InventoryRemoteService
) : InventoryRepository {
    constructor(
        baseUrl: String,
        token: String,
        companyRepository: CompanyRepository,
        json: Json = Json { ignoreUnknownKeys = true }
    ) : this(
        baseUrl = baseUrl,
        token = token,
        companyRepository = companyRepository,
        json = json,
        clientFactory = { HttpClient(Android) },
        remoteFactory = { client, remoteBaseUrl, remoteToken, remoteJson ->
            KtorInventoryRemoteService(client, remoteBaseUrl, remoteToken, remoteJson)
        }
    )

    internal constructor(
        companyRepository: CompanyRepository,
        clientFactory: () -> HttpClient,
        remoteFactory: (HttpClient) -> InventoryRemoteService
    ) : this(
        baseUrl = "",
        token = "",
        companyRepository = companyRepository,
        json = Json { ignoreUnknownKeys = true },
        clientFactory = clientFactory,
        remoteFactory = { client, _, _, _ -> remoteFactory(client) }
    )

    override suspend fun getInventories(): List<Inventory> {
        val companyId = companyRepository.requireCompanyId()
        val client = clientFactory()
        return try {
            remoteFactory(client, baseUrl, token, json)
                .getInventories(companyId)
        } finally {
            client.close()
        }
    }

    override suspend fun getInventory(inventoryId: Int): Inventory {
        val companyId = companyRepository.requireCompanyId()
        val client = clientFactory()
        return try {
            remoteFactory(client, baseUrl, token, json)
                .getInventory(companyId, inventoryId)
        } finally {
            client.close()
        }
    }
}
