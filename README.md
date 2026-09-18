# TWT Visitor Android SDK

The SDK opens a visitor chat page in a native WebView. The host application must provide an HTTPS URL; no test URL is bundled.

## Requirements and installation

Android minSdk 23; compileSdk/targetSdk 36 recommended; JDK 17, Gradle 8.14, AGP 8.13.2, Kotlin 2.2.20.

Source: https://github.com/TWT-Chat/twt-visitor-android

The artifact is on Maven Central. Hosts only need `mavenCentral()`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
dependencies { implementation("io.github.twt-chat:visitor-sdk:0.0.1") }
```

Flutter hosts using RepositoriesMode.PREFER_SETTINGS must also keep https://storage.googleapis.com/download.flutter.io.

## Quick start

```kotlin
val handle = TwtVisitorSdk.start(this, VisitorConfig(
    url = "https://visitor.example.com/direct/app",
    query = mapOf("visitor_id" to "user-123", "source" to "android"),
    isApp = true, title = "Online support",
    language = VisitorLanguage.EN, theme = VisitorTheme.SYSTEM,
    directChatId = null, newMessageSoundMode = VisitorMessageSoundMode.WEB,
), object : VisitorBridgeListener {
    override fun onDownloadRequested(request: VisitorDownloadRequest) {
        // Download, then call handle.reportDownloadStatus(...)
    }
})
```

start launches VisitorActivity and returns a session handle. A non-Activity Context receives FLAG_ACTIVITY_NEW_TASK automatically.

## VisitorConfig

| Field | Meaning |
| --- | --- |
| url | Required HTTPS URL; HTTP/file/custom schemes are rejected. |
| query | Business parameters, UTF-8 percent-encoded by the SDK; do not build a ? string manually. |
| isApp | Adds is_app=1 and shows a close button when true. |
| title | Native top title; an empty value uses the web title. |
| language | VisitorLanguage written to lang; default ZH_CN. |
| theme | LIGHT, DARK or SYSTEM written to theme. |
| directChatId | Direct conversation ID, written as direct=1&chatid=.... |
| newMessageSoundMode | WEB or NATIVE. |

Typed fields override same-name query keys. Never send passwords, AppSecret, cookies or long-lived tokens.

## API, callbacks and lifecycle

- TwtVisitorSdk.start(context[, config[, listener]]) creates a session.
- VisitorHandle.setTheme("light"/"dark") changes the WebView theme without reload.
- VisitorHandle.reportDownloadStatus sends VisitorDownloadStatus (started/completed/failed/cancelled; requestId must match).
- VisitorHandle.close() finishes the Activity; the handle is invalid afterwards.
- TwtVisitorSdk.clearSiteData(url, callback) asynchronously clears WebView data on the main thread; close and destroy every session for the host first.

VisitorBridgeListener is registered in start, called on the main thread, and unregistered when the Activity is destroyed: onDownloadRequested (requestId/url/type/fileName/mimeType/callback), onNewMessage (messageId/chatId/messageType/timestamp/isForeground), onBack (true means consumed; false permits closing), and onPermissionResult (requestId/granted/canAskAgain/resources). Deduplicate by requestId/messageId and mark unfinished downloads failed when the page is destroyed.

## Permissions, navigation and storage

The merged manifest contains INTERNET and RECORD_AUDIO. Only microphone requests from same-origin HTTPS pages enter runtime permission flow; camera and video requests are always denied. Android 9 and older may require host WRITE_EXTERNAL_STORAGE for public Downloads.

File inputs support single/multiple selection. Same-origin top-level navigation stays in the container; cross-origin HTTPS opens the system browser. HTTP, file, javascript and custom schemes are rejected. Cookies, Local Storage and IndexedDB persist by default; third-party cookies and mixed content are disabled.

## Cleanup, troubleshooting and release checks

clearSiteData returns SUCCESS, INVALID_URL, UNSUPPORTED, IN_USE or FAILED. IN_USE means an active session still uses the host. For account switching: close → wait for onDestroy → clear successfully → open the next account.

```bash
./gradlew :visitor-sdk:assembleRelease
./gradlew :sample:assembleDebug
./gradlew lint
```

On a real device verify microphone permission, file upload/download reporting, WSS, background/lock-screen behavior, weak-network retry, back navigation, external links and account isolation. There is no screen-capture bridge, and image clipboard support is not guaranteed on every system WebView.
