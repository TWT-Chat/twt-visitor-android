package com.twt.visitor.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.twt.visitor.sdk.TwtVisitorSdk
import com.twt.visitor.sdk.VisitorBridgeListener
import com.twt.visitor.sdk.VisitorDownloadRequest
import com.twt.visitor.sdk.VisitorLanguage
import com.twt.visitor.sdk.VisitorTheme
import com.twt.visitor.sdk.VisitorView

/**
 * Sample: the SDK provides a bare WebView, so the host draws the back button and title on this toolbar.
 * Use [TwtVisitorSdk.start] instead to open the SDK's own standalone page (no title bar).
 */
class EmbeddedVisitorActivity : Activity() {
    private lateinit var visitorView: VisitorView
    private lateinit var titleView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = try {
            VisitorSampleConfigParser.parse(
                VisitorSampleFormValues(
                    url = intent.getStringExtra(EXTRA_URL).orEmpty(),
                    query = intent.getStringExtra(EXTRA_QUERY).orEmpty(),
                    language = VisitorLanguage.entries.getOrNull(intent.getIntExtra(EXTRA_LANGUAGE, 0))
                        ?: VisitorLanguage.ZH_CN,
                    theme = VisitorTheme.entries.getOrNull(intent.getIntExtra(EXTRA_THEME, 0))
                        ?: VisitorTheme.SYSTEM,
                    directChatId = intent.getStringExtra(EXTRA_DIRECT_CHAT_ID).orEmpty(),
                ),
            )
        } catch (error: IllegalArgumentException) {
            Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        visitorView = TwtVisitorSdk.embed(this, config, object : VisitorBridgeListener {
            override fun onDownloadRequested(request: VisitorDownloadRequest) = Unit

            override fun onTitleChanged(title: String) {
                runOnUiThread { titleView.text = title }
            }
        })

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(createToolbar())
        root.addView(
            visitorView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)
        // Leave the status bar to the system so the toolbar and page sit below it, and inset the bottom for the navigation bar.
        root.applySystemBarInsets()
    }

    private fun createToolbar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56),
            )
            addView(TextView(this@EmbeddedVisitorActivity).apply {
                text = "\u2039"
                textSize = 30f
                gravity = Gravity.CENTER
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.MATCH_PARENT))
            titleView = TextView(this@EmbeddedVisitorActivity).apply {
                text = getString(R.string.embedded_title)
                textSize = 18f
                gravity = Gravity.CENTER
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(titleView)
            // Add a spacer as wide as the back button so the title really lands on the screen centre line.
            addView(View(this@EmbeddedVisitorActivity), LinearLayout.LayoutParams(dp(48), dp(1)))
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        // Ask the page first, then the host, and only then finish this activity.
        visitorView.handleBack(::finish)
    }

    override fun onResume() {
        super.onResume()
        visitorView.resumeSession()
    }

    override fun onPause() {
        visitorView.pauseSession()
        super.onPause()
    }

    override fun onDestroy() {
        visitorView.destroySession()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        visitorView.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        visitorView.onRequestPermissionsResult(requestCode, grantResults)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "com.twt.visitor.sample.extra.URL"
        const val EXTRA_QUERY = "com.twt.visitor.sample.extra.QUERY"
        const val EXTRA_LANGUAGE = "com.twt.visitor.sample.extra.LANGUAGE"
        const val EXTRA_THEME = "com.twt.visitor.sample.extra.THEME"
        const val EXTRA_DIRECT_CHAT_ID = "com.twt.visitor.sample.extra.DIRECT_CHAT_ID"
    }
}
