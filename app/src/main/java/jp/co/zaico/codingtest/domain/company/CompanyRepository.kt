package jp.co.zaico.codingtest.domain.company

/**
 * 会社IDの取得を担うDomain層の契約。
 */
interface CompanyRepository {
    /**
     * 拠点一覧から利用する会社IDを取得する。
     *
     * @return 会社IDの取得結果。
     */
    suspend fun getCompanyId(): CompanyIdResult

    /**
     * 利用する会社IDを取得し、取得できない場合は例外を送出する。
     *
     * @return 利用する会社ID。
     * @throws CompanyRepositoryException 会社IDを取得できない場合。
     */
    suspend fun requireCompanyId(): Int
}
