package com.twt.visitor.sdk

/** Outcome status of a site data cleanup. */
enum class VisitorSiteDataClearStatus { SUCCESS, INVALID_URL, UNSUPPORTED, IN_USE, FAILED }

/** Result delivered to the cleanup callback; [site] is platform diagnostic information only. */
data class VisitorSiteDataClearResult(
    val status: VisitorSiteDataClearStatus,
    val site: String? = null,
    val message: String? = null,
)

fun interface VisitorSiteDataClearCallback {
    fun onResult(result: VisitorSiteDataClearResult)
}
