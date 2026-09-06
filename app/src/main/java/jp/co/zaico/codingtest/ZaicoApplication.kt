package jp.co.zaico.codingtest

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import jp.co.zaico.codingtest.data.repository.CompanyRepository
import jp.co.zaico.codingtest.data.repository.InventoryCreator
import jp.co.zaico.codingtest.data.repository.InventoryRepository
import jp.co.zaico.codingtest.data.repository.KtorInventoryCreator
import jp.co.zaico.codingtest.data.repository.KtorInventoryRepository

class ZaicoApplication : Application() {
    lateinit var companyRepository: CompanyRepository
        private set

    internal var inventoryCreatorFactory: (() -> InventoryCreator)? = null
    internal var inventoryRepositoryFactory: (() -> InventoryRepository)? = null

    internal fun createInventoryRepository(): InventoryRepository =
        inventoryRepositoryFactory?.invoke() ?: KtorInventoryRepository(
            baseUrl = getString(R.string.api_endpoint),
            token = getString(R.string.api_token),
            companyRepository = companyRepository
        )

    internal fun createInventoryCreator(): InventoryCreator =
        inventoryCreatorFactory?.invoke() ?: KtorInventoryCreator(
            client = HttpClient(Android),
            baseUrl = getString(R.string.api_endpoint),
            token = getString(R.string.api_token),
            companyRepository = companyRepository
        )

    override fun onCreate() {
        super.onCreate()
        companyRepository = CompanyRepository(
            client = HttpClient(Android),
            baseUrl = getString(R.string.api_endpoint),
            token = getString(R.string.api_token),
            companiesPath = getString(R.string.companies_path)
        )
    }
}
