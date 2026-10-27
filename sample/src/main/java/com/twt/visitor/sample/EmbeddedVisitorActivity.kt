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
 * 示例：SDK提供裸 WebView，返回键和标题由宿主自己画在这条工具栏上。
 * 想复用 SDK 的独立页（无标题栏）就直接 [TwtVisitorSdk.start]。
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
        // 状态栏让出给系统，工具栏和网页都在它下面；底部再避让导航栏。
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
            // 右侧放一个和返回键等宽的占位，标题才真正落在屏幕中线上。
            addView(View(this@EmbeddedVisitorActivity), LinearLayout.LayoutParams(dp(48), dp(1)))
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        // 先交给网页，再交给宿主，最后退出这个页面。
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
