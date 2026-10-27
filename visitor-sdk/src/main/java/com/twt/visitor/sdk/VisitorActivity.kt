package com.twt.visitor.sdk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.view.WindowCompat
import com.twt.visitor.sdk.internal.VisitorInsetsApplier

/** Internal chat container for the SDK; hosts launch it through [TwtVisitorSdk] and never declare it themselves. */
class VisitorActivity : Activity(), VisitorSessionHost {
    private lateinit var view: VisitorView
    private var sessionId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        view = VisitorView(this)
        setContentView(view)
        VisitorInsetsApplier.apply(view)

        sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        if (sessionId.isNotBlank()) TwtVisitorSdk.registerActivity(sessionId, this)
        view.bind(
            sessionId = sessionId,
            rawUrl = intent.getStringExtra(EXTRA_URL),
            soundMode = intent.getStringExtra(EXTRA_SOUND_MODE) ?: "web",
            restoredState = savedInstanceState,
        )
    }

    override fun setTheme(theme: String) = view.setTheme(theme)

    override fun reportDownloadStatus(status: VisitorDownloadStatus) = view.reportDownloadStatus(status)

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        view.handleBack(::finish)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        view.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        view.resumeSession()
    }

    override fun onPause() {
        view.pauseSession()
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        view.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        view.onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        view.destroySession()
        if (sessionId.isNotBlank()) TwtVisitorSdk.unregister(sessionId)
        super.onDestroy()
    }

    companion object {
        internal const val EXTRA_SESSION_ID = "com.twt.visitor.sdk.extra.SESSION_ID"
        internal const val EXTRA_URL = "com.twt.visitor.sdk.extra.URL"
        internal const val EXTRA_SOUND_MODE = "com.twt.visitor.sdk.extra.SOUND_MODE"
    }
}

/** Internal contract that lets [TwtVisitorSdk.VisitorHandle] drive either container. */
internal interface VisitorSessionHost {
    fun setTheme(theme: String)

    fun reportDownloadStatus(status: VisitorDownloadStatus)
}
