package com.twt.visitor.sdk.internal

/** Minimal port for WebView back-navigation and teardown, keeping Activity lifecycle logic decoupled from the concrete widget. */
internal interface VisitorWebViewPort {
    val canGoBack: Boolean

    fun goBack()
    fun stopLoading()
    fun detach()
    fun destroy()
    fun onPause()
    fun onResume()
}

/** Centralizes web back-history handling and the WebView resource release order. */
internal class VisitorWebViewController(
    private val port: VisitorWebViewPort,
) {
    fun handleBack(close: () -> Unit) {
        if (port.canGoBack) {
            port.goBack()
        } else {
            close()
        }
    }

    fun destroy() {
        port.stopLoading()
        port.detach()
        port.destroy()
    }

    fun onPause() {
        port.onPause()
    }

    fun onResume() {
        port.onResume()
    }
}
