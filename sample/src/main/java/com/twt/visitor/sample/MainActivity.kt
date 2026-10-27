package com.twt.visitor.sample

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.twt.visitor.sdk.TwtVisitorSdk
import com.twt.visitor.sdk.VisitorLanguage
import com.twt.visitor.sdk.VisitorSiteDataClearCallback
import com.twt.visitor.sdk.VisitorTheme

/** Demonstrates a host app that depends on the visitor SDK through its Maven coordinates. */
class MainActivity : Activity() {
    private lateinit var urlInput: EditText
    private lateinit var queryInput: EditText
    private lateinit var languageInput: Spinner
    private lateinit var themeInput: Spinner
    private lateinit var directChatIdInput: EditText
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35+ 默认 edge-to-edge：显式关闭 fit，再用 insets 把内容放回状态栏下面。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val content = createContent()
        setContentView(content)
        content.applySystemBarInsets()
    }

    private fun createContent(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))

            addView(TextView(context).apply {
                text = getString(R.string.sample_description)
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(8))
            }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

            addView(fieldLabel(R.string.custom_url_label))
            urlInput = editText(
                hint = R.string.custom_url_hint,
                text = com.twt.visitor.sdk.VisitorConfig.DEFAULT_VISITOR_URL,
                singleLine = true,
            )
            addView(urlInput)

            addView(fieldLabel(R.string.custom_query_label))
            queryInput = editText(
                hint = R.string.custom_query_hint,
                singleLine = false,
            )
            addView(queryInput)

            addView(fieldLabel(R.string.custom_language_label))
            languageInput = spinner(
                VisitorLanguage.entries.map(VisitorLanguage::queryValue),
                VisitorLanguage.ZH_CN.queryValue,
            )
            addView(languageInput)

            addView(fieldLabel(R.string.custom_theme_label))
            themeInput = spinner(
                VisitorTheme.entries.map(VisitorTheme::queryValue),
                VisitorTheme.SYSTEM.queryValue,
            )
            addView(themeInput)

            addView(fieldLabel(R.string.custom_direct_chat_label))
            directChatIdInput = editText(
                hint = R.string.custom_direct_chat_hint,
                singleLine = true,
            )
            addView(directChatIdInput)

            statusView = TextView(context).apply {
                text = getString(R.string.sample_ready)
                textSize = 14f
                setPadding(0, dp(12), 0, 0)
            }
            addView(statusView)

            addView(Button(context).apply {
                text = getString(R.string.open_default)
                setOnClickListener {
                    openEmbedded(
                        VisitorSampleFormValues(
                            url = com.twt.visitor.sdk.VisitorConfig.DEFAULT_VISITOR_URL,
                        ),
                    )
                }
            }, buttonLayoutParams())
            addView(Button(context).apply {
                text = getString(R.string.open_custom)
                setOnClickListener { openCustomVisitor() }
            }, buttonLayoutParams())
            addView(Button(context).apply {
                text = getString(R.string.open_bare)
                setOnClickListener { openBareVisitor() }
            }, buttonLayoutParams())
            addView(Button(context).apply {
                text = getString(R.string.clear_site)
                setOnClickListener {
                    isEnabled = false
                    text = getString(R.string.clear_running)
                    TwtVisitorSdk.clearSiteData(callback = VisitorSiteDataClearCallback { result ->
                        isEnabled = true
                        text = getString(R.string.clear_site)
                        showStatus(
                            "${result.status}${result.site?.let { " ($it)" } ?: ""}",
                            isError = false,
                        )
                    })
                }
            }, buttonLayoutParams())
        }

        return ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
    }

    private fun formValues(): VisitorSampleFormValues? {
        val language = VisitorLanguage.entries.getOrNull(languageInput.selectedItemPosition)
        val theme = VisitorTheme.entries.getOrNull(themeInput.selectedItemPosition)
        if (language == null || theme == null) {
            showStatus(getString(R.string.custom_selection_error), isError = true)
            return null
        }
        return VisitorSampleFormValues(
            url = urlInput.text.toString(),
            query = queryInput.text.toString(),
            language = language,
            theme = theme,
            directChatId = directChatIdInput.text.toString(),
        )
    }

    /** 裸页面直接交给 SDK 的 Activity；它同样要合法 URL，不合法就在表单里报错而不是崩溃。 */
    private fun openBareVisitor() {
        val values = formValues() ?: return
        try {
            TwtVisitorSdk.start(this, VisitorSampleConfigParser.parse(values))
        } catch (error: IllegalArgumentException) {
            showStatus(
                getString(R.string.custom_validation_error, error.message ?: "Invalid parameters"),
                isError = true,
            )
        }
    }

    private fun openCustomVisitor() {
        val values = formValues() ?: return
        openEmbedded(values)
    }

    /** 打开示例自己的页面：状态栏 + 返回键和标题都由宿主绘制；参数不合法就在表单里报错。 */
    private fun openEmbedded(values: VisitorSampleFormValues) {
        try {
            // Validate here so malformed input is reported in the form instead of the next screen.
            VisitorSampleConfigParser.parse(values)
        } catch (error: IllegalArgumentException) {
            showStatus(
                getString(R.string.custom_validation_error, error.message ?: "Invalid parameters"),
                isError = true,
            )
            return
        }
        showStatus(getString(R.string.custom_opening), isError = false)
        startActivity(
            Intent(this, EmbeddedVisitorActivity::class.java)
                .putExtra(EmbeddedVisitorActivity.EXTRA_URL, values.url)
                .putExtra(EmbeddedVisitorActivity.EXTRA_QUERY, values.query)
                .putExtra(EmbeddedVisitorActivity.EXTRA_LANGUAGE, values.language.ordinal)
                .putExtra(EmbeddedVisitorActivity.EXTRA_THEME, values.theme.ordinal)
                .putExtra(EmbeddedVisitorActivity.EXTRA_DIRECT_CHAT_ID, values.directChatId),
        )
    }

    private fun fieldLabel(label: Int): TextView {
        return TextView(this).apply {
            text = getString(label)
            textSize = 14f
            setPadding(0, dp(14), 0, dp(4))
        }
    }

    private fun editText(
        hint: Int,
        text: String = "",
        singleLine: Boolean,
    ): EditText {
        return EditText(this).apply {
            this.hint = getString(hint)
            this.setText(text)
            this.isSingleLine = singleLine
            if (!singleLine) minLines = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun spinner(values: List<String>, selectedValue: String): Spinner {
        return Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                values,
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(values.indexOf(selectedValue).coerceAtLeast(0))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        if (!::statusView.isInitialized) return
        statusView.text = message
        statusView.setTextColor(if (isError) Color.rgb(183, 28, 28) else Color.DKGRAY)
        if (isError) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun buttonLayoutParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(16)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
