package com.twt.visitor.sdk

/**
 * Launch configuration for the visitor page.
 *
 * The URL must use HTTPS; the SDK encodes query parameters, so callers must not build them manually.
 */
data class VisitorConfig @JvmOverloads constructor(
    val url: String = DEFAULT_VISITOR_URL,
    val query: Map<String, String> = emptyMap(),
    val title: String? = null,
    val language: VisitorLanguage = VisitorLanguage.ZH_CN,
    val theme: VisitorTheme = VisitorTheme.SYSTEM,
    val directChatId: String? = null,
    val newMessageSoundMode: VisitorMessageSoundMode = VisitorMessageSoundMode.WEB,
    /** Whether the page is opened from an app; when enabled the URL carries is_app=1 for the visitor page to detect. */
    val isApp: Boolean = false,
) {
    companion object {
        /** The host must pass its own HTTPS visitor URL; the SDK bundles no test URL. */
        const val DEFAULT_VISITOR_URL = ""
    }
}

enum class VisitorMessageSoundMode(val queryValue: String) { NATIVE("native"), WEB("web") }
