package com.twt.visitor.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.twt.visitor.sdk.internal.VisitorUrlPolicy
import com.twt.visitor.sdk.internal.AndroidWebStoragePort
import com.twt.visitor.sdk.internal.VisitorSiteDataCleaner
import com.twt.visitor.sdk.internal.VisitorSessionRegistryHolder

/** Public entry point of the Android visitor SDK. */
object TwtVisitorSdk {
    private val listeners = ConcurrentHashMap<String, VisitorBridgeListener>()
    private val activities = ConcurrentHashMap<String, VisitorActivity>()

    /** Handle for an active visitor session; each page owns its own listener/sessionId. */
    class VisitorHandle internal constructor(val sessionId: String) {
        fun setTheme(theme: String) { activities[sessionId]?.setTheme(theme) }
        fun reportDownloadStatus(status: VisitorDownloadStatus) { activities[sessionId]?.reportDownloadStatus(status) }
        fun close() { activities[sessionId]?.finish() }
    }

    internal fun listener(sessionId: String): VisitorBridgeListener? = listeners[sessionId]
    internal fun registerActivity(sessionId: String, activity: VisitorActivity) { activities[sessionId] = activity }
    internal fun unregister(sessionId: String) { activities.remove(sessionId); listeners.remove(sessionId) }
    /** Opens the chat page with the default configuration; the host should pass its own visitor URL. */
    @JvmStatic
    fun start(context: Context): VisitorHandle {
        return start(context, VisitorConfig(), null)
    }

    /** Opens the chat page with the given HTTPS URL and visitor parameters. */
    @JvmStatic
    fun start(context: Context, config: VisitorConfig): VisitorHandle {
        return start(context, config, null)
    }

    @JvmStatic
    @JvmOverloads
    fun start(context: Context, config: VisitorConfig, listener: VisitorBridgeListener?): VisitorHandle {
        val sessionId = UUID.randomUUID().toString()
        if (listener != null) listeners[sessionId] = listener
        val intent = Intent(context, VisitorActivity::class.java).apply {
            putExtra(VisitorActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(VisitorActivity.EXTRA_URL, VisitorUrlPolicy.buildUrl(config))
            config.title?.let { putExtra(VisitorActivity.EXTRA_TITLE, it) }
            putExtra(VisitorActivity.EXTRA_SOUND_MODE, config.newMessageSoundMode.queryValue)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return VisitorHandle(sessionId)
    }

    /** Asynchronously clears persistent website data for the target WebView site. */
    @JvmStatic
    @JvmOverloads
    fun clearSiteData(
        url: String = VisitorConfig.DEFAULT_VISITOR_URL,
        callback: VisitorSiteDataClearCallback,
    ) {
        Handler(Looper.getMainLooper()).post {
            VisitorSiteDataCleaner(VisitorSessionRegistryHolder.shared, AndroidWebStoragePort()).clear(url, callback)
        }
    }
}
