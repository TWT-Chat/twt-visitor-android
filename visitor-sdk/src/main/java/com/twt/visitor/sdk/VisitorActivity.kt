package com.twt.visitor.sdk

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import org.json.JSONObject
import android.widget.Button
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.twt.visitor.sdk.internal.FileChooserResultParser
import com.twt.visitor.sdk.internal.PendingFileChooser
import com.twt.visitor.sdk.internal.VisitorNavigation
import com.twt.visitor.sdk.internal.VisitorNavigationPolicy
import com.twt.visitor.sdk.internal.VisitorPermissionPolicy
import com.twt.visitor.sdk.internal.VisitorInsetsApplier
import com.twt.visitor.sdk.internal.VisitorSessionRegistry
import com.twt.visitor.sdk.internal.VisitorSessionRegistryHolder
import com.twt.visitor.sdk.internal.VisitorUrlPolicy
import com.twt.visitor.sdk.internal.VisitorWebViewController
import com.twt.visitor.sdk.internal.VisitorWebViewPort

/** Internal chat container for the SDK; hosts launch it through [TwtVisitorSdk] and never declare it themselves. */
class VisitorActivity : Activity() {
    private lateinit var titleView: TextView
    private lateinit var webView: WebView
    private lateinit var errorView: View
    private lateinit var controller: VisitorWebViewController
    private val fileChooser = PendingFileChooser()
    private var targetUrl: String? = null
    private var soundMode: String = "web"
    private var configuredTitle: String? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var pendingWebResources: List<String> = emptyList()
    private var pendingPermissionId: String = ""
    private var sessionToken: VisitorSessionRegistry.Token? = null
    private var sessionId: String = ""
    private var externalNavigationUrl: String? = null
    private var suppressedExternalErrorUrl: String? = null
    private var externalNavigationLauncher: (Uri) -> Unit = { uri ->
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
    private var destroyed = false
    private var hasLoadedSuccessfully = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.twt_visitor_activity)

        titleView = findViewById(R.id.twt_visitor_title)
        webView = findViewById(R.id.twt_visitor_web_view)
        errorView = findViewById(R.id.twt_visitor_error)
        controller = VisitorWebViewController(AndroidWebViewPort(webView))
        VisitorInsetsApplier.apply(findViewById(R.id.twt_visitor_root))

        findViewById<View>(R.id.twt_visitor_back).setOnClickListener { handleBack() }
        findViewById<Button>(R.id.twt_visitor_retry).setOnClickListener { loadTarget() }

        configuredTitle = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        if (sessionId.isNotBlank()) TwtVisitorSdk.registerActivity(sessionId, this)
        soundMode = intent.getStringExtra(EXTRA_SOUND_MODE) ?: "web"
        configuredTitle?.let(titleView::setText)
        configureWebView()

        targetUrl = validateIntentUrl(intent.getStringExtra(EXTRA_URL))
        if (targetUrl == null) {
            showError()
        } else {
            val host = Uri.parse(targetUrl).host.orEmpty()
            sessionToken = VisitorSessionRegistryHolder.shared.acquire(host)
            if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) loadTarget()
        }
    }

    @Suppress("DEPRECATION", "SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
        }
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = VisitorClient()
        webView.webChromeClient = VisitorChromeClient()
        webView.addJavascriptInterface(object {
            @JavascriptInterface fun postMessage(raw: String) { handleBridge(raw) }
        }, "AppBridgeChannel_native")
        webView.setDownloadListener { url, _, disposition, mime, _ ->
            val id = "visitor_${System.currentTimeMillis()}"
            TwtVisitorSdk.listener(sessionId)?.onDownloadRequested(VisitorDownloadRequest(id, url, if (mime?.startsWith("video") == true) "video" else if (mime?.startsWith("image") == true) "image" else "file", android.webkit.URLUtil.guessFileName(url, disposition, mime), mime))
        }
    }

    private fun validateIntentUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        return runCatching { VisitorUrlPolicy.validateUrl(rawUrl) }.getOrNull()
    }

    private fun loadTarget() {
        val url = targetUrl ?: return showError()
        externalNavigationUrl = null
        suppressedExternalErrorUrl = null
        errorView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(url)
    }

    private fun showError() {
        webView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    /** Shows the retryable error state when the main frame fails to load. */
    internal fun handleMainFrameLoadError() {
        showError()
    }

    /** Delivers the file chooser result to the pending web callback; safely ignored when no request is pending. */
    internal fun completeFileChooserForResult(resultCode: Int, data: Intent?) {
        fileChooser.complete(FileChooserResultParser.parse(resultCode, data))
    }

    /** Sets the pending file callback for WebChromeClient and instrumentation tests. */
    internal fun replaceFileChooserCallback(callback: ValueCallback<Array<Uri>>) {
        fileChooser.replace(callback)
    }

    private fun handleBack() {
        // Give the H5 page 300ms to consume the event before navigating back or closing;
        // reading the JS state synchronously would race with the page.
        webView.evaluateJavascript("(function(){window.__twtVisitorBackConsumed=false;window.dispatchEvent(new CustomEvent('twt:visitor-event',{detail:{type:'back',payload:{sessionId:'$sessionId'}}}));return true;})()", null)
        android.os.Handler(mainLooper).postDelayed({
            webView.evaluateJavascript("!!window.__twtVisitorBackConsumed") { raw ->
                val consumed = raw == "true"
                if (!consumed && TwtVisitorSdk.listener(sessionId)?.onBack() != true) controller.handleBack(::finish)
            }
        }, 300)
    }

    private fun handleBridge(raw: String) {
        runCatching {
            val json = JSONObject(raw); val params = json.optJSONObject("params")
            when (json.optString("method")) {
                "download" -> { val id = json.optString("requestId", params?.optString("requestId").orEmpty()); val callback = json.optString("callback"); if (callback.isNotBlank()) statusCallbackIds[id] = callback; TwtVisitorSdk.listener(sessionId)?.onDownloadRequested(VisitorDownloadRequest(id, params?.optString("url").orEmpty(), params?.optString("type").orEmpty(), params?.optString("fileName"), params?.optString("mimeType"), callback)) }
                "new_message" -> TwtVisitorSdk.listener(sessionId)?.onNewMessage(VisitorNewMessage(params?.optString("messageId"), params?.optString("chatId"), params?.optString("messageType"), params?.optLong("timestamp", System.currentTimeMillis()) ?: System.currentTimeMillis(), params?.optBoolean("isForeground", true) ?: true))
            }
        }
    }

    internal fun reportDownloadStatus(status: VisitorDownloadStatus) {
        val callback = statusCallbackIds[status.requestId] ?: return
        val payload = JSONObject().apply { put("requestId", status.requestId); put("status", status.status); status.progress?.let { put("progress", it) }; status.sourceUrl?.let { put("sourceUrl", it) }; status.path?.let { put("path", it) }; status.mimeType?.let { put("mimeType", it) }; status.bytes?.let { put("bytes", it) }; status.errorCode?.let { put("errorCode", it) }; status.message?.let { put("message", it) } }.toString()
        webView.post { webView.evaluateJavascript("window.AppBridge && window.AppBridge.invokeCallback(${JSONObject.quote(callback)}, {code:${if (status.status == "failed") 0 else 1},data:$payload})", null) }
        if (status.status == "completed" || status.status == "failed" || status.status == "cancelled") {
            statusCallbackIds.remove(status.requestId)
        }
    }
    internal fun setTheme(theme: String) { if (theme == "dark" || theme == "light") webView.post { webView.evaluateJavascript("window.AppBridge && window.AppBridge.emitEvent('themeChanged', {theme:'${theme}'})", null) } }
    private val statusCallbackIds = mutableMapOf<String, String>()

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        handleBack()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        controller.onResume()
    }

    override fun onPause() {
        controller.onPause()
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_CHOOSER) {
            completeFileChooserForResult(resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_WEB_PERMISSION) return
        completeWebPermissionRequest()
    }

    override fun onDestroy() {
        if (destroyed) return super.onDestroy()
        destroyed = true
        fileChooser.complete(null)
        pendingPermissionRequest?.deny()
        pendingPermissionRequest = null
        controller.destroy()
        if (sessionId.isNotBlank()) TwtVisitorSdk.unregister(sessionId)
        sessionToken?.release()
        sessionToken = null
        super.onDestroy()
    }

    private inner class VisitorClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            return handleNavigation(request.url.toString())
        }

        @Deprecated("Deprecated in Android")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            // The legacy string callback has no isForMainFrame: cancel dangerous schemes here and let
            // cross-origin HTTPS through to onPageStarted, which only fires for the main frame, so
            // iframes are not affected.
            return when (targetUrl?.let { VisitorNavigationPolicy.classify(it, url) }
                ?: VisitorNavigation.Blocked) {
                VisitorNavigation.WebView,
                VisitorNavigation.ExternalBrowser,
                -> false
                VisitorNavigation.Blocked -> true
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            view.evaluateJavascript(BRIDGE_SCRIPT.replace("__MODE__", soundMode).replace("__SESSION__", sessionId), null)
            when (targetUrl?.let { VisitorNavigationPolicy.classify(it, url) }
                ?: VisitorNavigation.Blocked) {
                VisitorNavigation.WebView -> {
                    externalNavigationUrl = null
                    suppressedExternalErrorUrl = null
                    errorView.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                }
                VisitorNavigation.ExternalBrowser -> {
                    // onPageStarted is guaranteed to describe the main frame. Stop before
                    // handing the URL to another app so an API 23 legacy callback cannot leave
                    // a cross-origin page in this WebView.
                    // Mark the URL before stopping: WebView may synchronously report the
                    // cancellation through onReceivedError.
                    suppressedExternalErrorUrl = url
                    view.stopLoading()
                    openExternalNavigation(url)
                }
                VisitorNavigation.Blocked -> {
                    view.stopLoading()
                    handleMainFrameLoadError()
                }
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame && !hasLoadedSuccessfully && !isSuppressedExternalError(request.url.toString())) {
                handleMainFrameLoadError()
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            hasLoadedSuccessfully = true
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (request.isForMainFrame && !hasLoadedSuccessfully && !isSuppressedExternalError(request.url.toString())) {
                handleMainFrameLoadError()
            }
        }

        private fun handleNavigation(url: String): Boolean {
            val baseUrl = targetUrl ?: return true
            return when (VisitorNavigationPolicy.classify(baseUrl, url)) {
                VisitorNavigation.WebView -> false
                VisitorNavigation.ExternalBrowser -> {
                    openExternalNavigation(url)
                    true
                }
                VisitorNavigation.Blocked -> true
            }
        }

        private fun openExternalNavigation(url: String) {
            if (externalNavigationUrl == url) return
            externalNavigationUrl = url
            val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
            externalNavigationLauncher(uri)
        }

        private fun isSuppressedExternalError(url: String): Boolean {
            return url == suppressedExternalErrorUrl || url == externalNavigationUrl
        }
    }

    /** Replaces external navigation for instrumentation tests without requiring a system handler. */
    internal fun replaceExternalNavigationLauncher(launcher: (Uri) -> Unit): () -> Unit {
        val previous = externalNavigationLauncher
        externalNavigationLauncher = launcher
        return { externalNavigationLauncher = previous }
    }

    private inner class VisitorChromeClient : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String?) {
            if (configuredTitle == null && !title.isNullOrBlank()) titleView.text = title
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            replaceFileChooserCallback(filePathCallback)
            val chooserIntent = runCatching { fileChooserParams.createIntent() }.getOrNull()
                ?: return true.also { fileChooser.complete(null) }
            chooserIntent.addCategory(Intent.CATEGORY_OPENABLE)
            chooserIntent.putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE,
            )
            return runCatching {
                startActivityForResult(chooserIntent, REQUEST_FILE_CHOOSER)
                true
            }.getOrElse {
                fileChooser.complete(null)
                true
            }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread {
                handleWebPermissionRequest(request)
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (pendingPermissionRequest == request) {
                pendingPermissionRequest = null
                pendingWebResources = emptyList()
            }
        }
    }

    /** Maps trusted web resources to Android runtime permissions; tests can inject permission state. */
    internal fun handleWebPermissionRequest(
        request: PermissionRequest,
        permissionGranted: (String) -> Boolean = {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        },
        requestPermissions: (Array<String>) -> Unit = {
            requestPermissions(it, REQUEST_WEB_PERMISSION)
        },
    ) {
        val baseUrl = targetUrl ?: return request.deny()
        val allowed = VisitorPermissionPolicy.allowedResources(
            baseUrl,
            request.origin.toString(),
            request.resources.toList(),
        )
        if (allowed.isEmpty()) return request.deny()

        pendingPermissionRequest?.deny()
        pendingPermissionRequest = request
        pendingWebResources = allowed
        pendingPermissionId = "permission_${System.currentTimeMillis()}"
        val missing = requiredAndroidPermissions(allowed).filterNot(permissionGranted)
        if (missing.isEmpty()) {
            completeWebPermissionRequest(permissionGranted)
        } else {
            requestPermissions(missing.toTypedArray())
        }
    }

    /** Completes one pending web permission request based on the current Android permission result. */
    internal fun completeWebPermissionRequest(
        permissionGranted: (String) -> Boolean = {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        },
    ) {
        val request = pendingPermissionRequest ?: return
        val grantedResources = pendingWebResources.filter { resource ->
            requiredAndroidPermissions(listOf(resource)).all(permissionGranted)
        }
        pendingPermissionRequest = null
        pendingWebResources = emptyList()
        val granted = grantedResources.isNotEmpty()
        if (!granted) request.deny() else request.grant(grantedResources.toTypedArray())
        TwtVisitorSdk.listener(sessionId)?.onPermissionResult(VisitorPermissionResult(pendingPermissionId, granted, if (granted) true else shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO), grantedResources))
        pendingPermissionId = ""
    }

    private fun requiredAndroidPermissions(resources: List<String>): List<String> {
        return buildList {
            if (VisitorPermissionPolicy.AUDIO_CAPTURE in resources) add(Manifest.permission.RECORD_AUDIO)
            if (VisitorPermissionPolicy.VIDEO_CAPTURE in resources) add(Manifest.permission.CAMERA)
        }
    }

    private class AndroidWebViewPort(
        private val webView: WebView,
    ) : VisitorWebViewPort {
        override val canGoBack: Boolean
            get() = webView.canGoBack()

        override fun goBack() = webView.goBack()

        override fun stopLoading() = webView.stopLoading()

        override fun detach() {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }

        override fun destroy() = webView.destroy()

        override fun onPause() = webView.onPause()

        override fun onResume() = webView.onResume()
    }

    companion object {
        private const val BRIDGE_SCRIPT = """(function(){window.__TWT_VISITOR_SESSION_ID__='__SESSION__';window.__TWT_VISITOR_BRIDGE_CONFIG__={sessionId:'__SESSION__',newMessageSoundMode:'__MODE__'};if(window.AppBridge)return;window.AppBridge={_callbacks:{},call:function(m,p,cb){var id=cb?'cb_'+Date.now()+'_'+Math.random().toString(16).slice(2):null;if(cb)this._callbacks[id]=cb;p=Object.assign({sessionId:window.__TWT_VISITOR_SESSION_ID__},p||{});window.AppBridgeChannel_native.postMessage(JSON.stringify({method:m,params:p,callback:id,requestId:p&&p.requestId}));return Promise.resolve({status:'sent',callbackId:id});},invokeCallback:function(id,r){var cb=this._callbacks[id];if(cb){cb(r);var data=r&&r.data&&typeof r.data==='object'?r.data:r;var status=data&&data.status;if(status==='completed'||status==='failed'||status==='cancelled')delete this._callbacks[id];}},emitEvent:function(t,p){window.dispatchEvent(new CustomEvent('twt:visitor-event',{detail:{type:t,payload:Object.assign({sessionId:window.__TWT_VISITOR_SESSION_ID__},p||{})}}));}};})();"""
        internal const val EXTRA_SESSION_ID = "com.twt.visitor.sdk.extra.SESSION_ID"
        internal const val EXTRA_URL = "com.twt.visitor.sdk.extra.URL"
        internal const val EXTRA_TITLE = "com.twt.visitor.sdk.extra.TITLE"
        internal const val EXTRA_SOUND_MODE = "com.twt.visitor.sdk.extra.SOUND_MODE"
        private const val REQUEST_FILE_CHOOSER = 1001
        private const val REQUEST_WEB_PERMISSION = 1002
    }
}
