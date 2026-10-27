package com.twt.visitor.sdk

/** Bridge callbacks for the native visitor page; the host performs downloads and reports status back. */
interface VisitorBridgeListener {
    fun onDownloadRequested(request: VisitorDownloadRequest)
    fun onNewMessage() {}
    fun onNewMessage(message: VisitorNewMessage) { onNewMessage() }
    fun onBack(): Boolean = false
    fun onPermissionResult(result: VisitorPermissionResult) {}

    /** Web page title; the SDK renders no title bar, so the host shows it in its own UI. */
    fun onTitleChanged(title: String) {}
}

data class VisitorNewMessage(
    val messageId: String? = null,
    val chatId: String? = null,
    val messageType: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isForeground: Boolean = true,
)

data class VisitorPermissionResult(
    val requestId: String,
    val granted: Boolean,
    val canAskAgain: Boolean,
    val resources: List<String> = emptyList(),
)

data class VisitorDownloadRequest(
    val requestId: String,
    val url: String,
    val type: String,
    val fileName: String? = null,
    val mimeType: String? = null,
    val callback: String? = null,
)

data class VisitorDownloadStatus(
    val requestId: String,
    val status: String,
    val progress: Int? = null,
    val sourceUrl: String? = null,
    val path: String? = null,
    val mimeType: String? = null,
    val bytes: Long? = null,
    val errorCode: String? = null,
    val message: String? = null,
)
