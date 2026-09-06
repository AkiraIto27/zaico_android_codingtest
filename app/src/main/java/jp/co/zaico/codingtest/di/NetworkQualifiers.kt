package jp.co.zaico.codingtest.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiBaseUrl

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiToken

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CompaniesPath
