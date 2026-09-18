package com.twt.visitor.sdk.internal

import java.net.URI

/** Destination classification for visitor page navigation. */
internal enum class VisitorNavigation {
    WebView,
    ExternalBrowser,
    Blocked,
}

/** Restricts in-container navigation to same-origin pages; external pages go to the system browser. */
internal object VisitorNavigationPolicy {
    fun classify(baseUrl: String, candidateUrl: String): VisitorNavigation {
        val candidate = runCatching { URI(candidateUrl) }.getOrNull()
            ?: return VisitorNavigation.Blocked
        if (candidate.host.isNullOrBlank()) return VisitorNavigation.Blocked
        if (candidate.port != -1 && candidate.port !in 1..65535) return VisitorNavigation.Blocked

        return when {
            VisitorUrlPolicy.isSameOrigin(baseUrl, candidateUrl) -> VisitorNavigation.WebView
            candidate.scheme.equals("https", ignoreCase = true) -> VisitorNavigation.ExternalBrowser
            else -> VisitorNavigation.Blocked
        }
    }
}
