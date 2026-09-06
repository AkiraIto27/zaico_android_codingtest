package jp.co.zaico.codingtest.domain.company

/**
 * 会社IDを取得できない場合に発生する例外。
 */
class CompanyRepositoryException(val result: CompanyIdResult) : Exception()
