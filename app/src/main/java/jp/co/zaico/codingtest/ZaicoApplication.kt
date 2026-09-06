package jp.co.zaico.codingtest

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import jp.co.zaico.codingtest.data.repository.CompanyRepositoryImpl
import jp.co.zaico.codingtest.data.repository.InventoryCreator
import jp.co.zaico.codingtest.data.repository.KtorInventoryCreator
import jp.co.zaico.codingtest.data.repository.DefaultInventoryRepository
import jp.co.zaico.codingtest.data.remote.KtorInventoryRemoteService
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository
import jp.co.zaico.codingtest.domain.company.CompanyRepository
import kotlinx.serialization.json.Json

class ZaicoApplication : Application() {
    lateinit var companyRepository: CompanyRepository
        private set

    internal var inventoryCreatorFactory: (() -> InventoryCreator)? = null
    internal var inventoryRepositoryFactory: (() -> InventoryRepository)? = null

    internal fun createInventoryRepository(): InventoryRepository =
        inventoryRepositoryFactory?.invoke() ?: DefaultInventoryRepository(
            baseUrl = getString(R.string.api_endpoint),
            token = getString(R.string.api_token),
            companyRepository = companyRepository
        )

    internal fun createInventoryCreator(): InventoryCreator {
        inventoryCreatorFactory?.let { return it.invoke() }
        val client = HttpClient(Android)
        val baseUrl = getString(R.string.api_endpoint)
        val token = getString(R.string.api_token)
        val json = Json { ignoreUnknownKeys = true }
        return KtorInventoryCreator(
            client = client,
            token = token,
            companyRepository = companyRepository,
            remote = KtorInventoryRemoteService(client, baseUrl, token, json)
        )
    }

    override fun onCreate() {
        super.onCreate()
        companyRepository = CompanyRepositoryImpl(
            client = HttpClient(Android),
            baseUrl = getString(R.string.api_endpoint),
            token = getString(R.string.api_token),
            companiesPath = getString(R.string.companies_path)
        )
    }
}
