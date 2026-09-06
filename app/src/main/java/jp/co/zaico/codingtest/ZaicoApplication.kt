package jp.co.zaico.codingtest

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android

class ZaicoApplication : Application() {
    lateinit var companyRepository: CompanyRepository
        private set

    internal var inventoryCreatorFactory: (() -> InventoryCreator)? = null

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
