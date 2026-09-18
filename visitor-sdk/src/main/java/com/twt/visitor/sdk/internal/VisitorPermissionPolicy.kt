package com.twt.visitor.sdk.internal

/** Allows only trusted pages to request WebView microphone and camera resources. */
internal object VisitorPermissionPolicy {
    const val AUDIO_CAPTURE = "android.webkit.resource.AUDIO_CAPTURE"
    const val VIDEO_CAPTURE = "android.webkit.resource.VIDEO_CAPTURE"

    private val supportedResources = setOf(AUDIO_CAPTURE, VIDEO_CAPTURE)

    fun allowedResources(
        baseUrl: String,
        origin: String,
        resources: List<String>,
    ): List<String> {
        if (!VisitorUrlPolicy.isSameOrigin(baseUrl, origin)) return emptyList()
        return resources.filter(supportedResources::contains).distinct()
    }
}
