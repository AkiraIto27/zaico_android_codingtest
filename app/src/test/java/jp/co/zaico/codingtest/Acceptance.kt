package jp.co.zaico.codingtest

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Acceptance(vararg val value: String)
