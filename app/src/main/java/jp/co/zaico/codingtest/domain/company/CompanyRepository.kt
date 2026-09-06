package jp.co.zaico.codingtest.domain.company

/**
 * 拠点一覧から在庫処理に使用する拠点IDを解決するDomain契約。
 */
interface CompanyRepository {
    suspend fun getCompanyId(): CompanyIdResult

    suspend fun requireCompanyId(): Int
}

sealed interface CompanyIdResult {
    data class Success(val companyId: Int) : CompanyIdResult
    data object Empty : CompanyIdResult
    data object ConfigurationFailure : CompanyIdResult
    data class HttpFailure(val statusCode: Int) : CompanyIdResult
    data object DecodeFailure : CompanyIdResult
    data object NetworkFailure : CompanyIdResult
}

class CompanyRepositoryException(val result: CompanyIdResult) : Exception()
