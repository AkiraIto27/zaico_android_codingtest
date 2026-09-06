package jp.co.zaico.codingtest.domain.company

/**
 * 会社IDの取得結果を表すDomainモデル。
 */
sealed interface CompanyIdResult {
    data class Success(val companyId: Int) : CompanyIdResult
    data object Empty : CompanyIdResult
    data object ConfigurationFailure : CompanyIdResult
    data class HttpFailure(val statusCode: Int) : CompanyIdResult
    data object DecodeFailure : CompanyIdResult
    data object NetworkFailure : CompanyIdResult
}
