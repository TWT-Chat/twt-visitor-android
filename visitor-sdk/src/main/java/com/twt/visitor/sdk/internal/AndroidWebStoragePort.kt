package com.twt.visitor.sdk.internal

import android.webkit.WebStorage
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature

/** Adapter over AndroidX WebKit site-scoped browsing data deletion. */
internal class AndroidWebStoragePort(
    private val storage: WebStorage = WebStorage.getInstance(),
) : VisitorSiteDataPort {
    override val isSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)

    override fun deleteForSite(url: String, completion: (String) -> Unit): String {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            throw UnsupportedOperationException("Site data cleanup is unavailable")
        }
        lateinit var site: String
        site = WebStorageCompat.deleteBrowsingDataForSite(storage, url) { completion(site) }
        return site
    }
}
