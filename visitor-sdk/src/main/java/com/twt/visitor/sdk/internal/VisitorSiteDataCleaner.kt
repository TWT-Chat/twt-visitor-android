package com.twt.visitor.sdk.internal

import android.os.Handler
import android.os.Looper
import com.twt.visitor.sdk.VisitorSiteDataClearCallback
import com.twt.visitor.sdk.VisitorSiteDataClearResult
import com.twt.visitor.sdk.VisitorSiteDataClearStatus
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

internal interface VisitorSiteDataPort {
    val isSupported: Boolean
    fun deleteForSite(url: String, completion: (String) -> Unit): String
}

internal interface VisitorCallbackDispatcher { fun dispatch(block: () -> Unit) }

internal object MainThreadDispatcher : VisitorCallbackDispatcher {
    private val handler = Handler(Looper.getMainLooper())
    override fun dispatch(block: () -> Unit) = handler.post(block).let { Unit }
}

/** Site cleanup state machine; it never falls back to a global Cookie/WebStorage wipe. */
internal class VisitorSiteDataCleaner(
    private val registry: VisitorSessionRegistry,
    private val port: VisitorSiteDataPort,
    private val dispatcher: VisitorCallbackDispatcher = MainThreadDispatcher,
) {
    fun clear(url: String, callback: VisitorSiteDataClearCallback) {
        val completed = AtomicBoolean(false)
        fun complete(result: VisitorSiteDataClearResult) {
            if (completed.compareAndSet(false, true)) dispatcher.dispatch { callback.onResult(result) }
        }
        val validatedUrl = runCatching { VisitorUrlPolicy.validateUrl(url) }.getOrNull()
        if (validatedUrl == null) {
            complete(VisitorSiteDataClearResult(VisitorSiteDataClearStatus.INVALID_URL)); return
        }
        val targetHost = runCatching { URI(validatedUrl).host.orEmpty() }.getOrDefault("")
        if (registry.hasActiveSessionsFor(targetHost)) {
            complete(VisitorSiteDataClearResult(VisitorSiteDataClearStatus.IN_USE)); return
        }
        if (!port.isSupported) { complete(VisitorSiteDataClearResult(VisitorSiteDataClearStatus.UNSUPPORTED)); return }
        runCatching {
            port.deleteForSite(validatedUrl) { site -> complete(VisitorSiteDataClearResult(VisitorSiteDataClearStatus.SUCCESS, site = site)) }
        }.onFailure { error ->
            val status = when (error) {
                is IllegalArgumentException -> VisitorSiteDataClearStatus.INVALID_URL
                is UnsupportedOperationException -> VisitorSiteDataClearStatus.UNSUPPORTED
                else -> VisitorSiteDataClearStatus.FAILED
            }
            complete(VisitorSiteDataClearResult(status, message = "Site data cleanup failed"))
        }
    }
}
