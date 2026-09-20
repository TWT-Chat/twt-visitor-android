# TWT Visitor Android SDK

Embed a visitor chat page in your app with a native WebView.

**Version:** 0.0.1 · **minSdk:** 23 · **License:** Proprietary

## Installation

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

dependencies {
    implementation("io.github.twt-chat:visitor-sdk:0.0.1")
}
```

The SDK already merges `INTERNET`, `RECORD_AUDIO`, and `CAMERA` (`required=false`). On Android 9 and below, add `WRITE_EXTERNAL_STORAGE` if you save downloads to public Downloads.

## Quick start

The visitor URL must be HTTPS. Put business parameters in `query` — do not build a query string yourself, and do not put secrets in the URL.

```kotlin
val handle = TwtVisitorSdk.start(
    this,
    VisitorConfig(url = "URL from the console"),
)
```

`start` opens `VisitorActivity` and returns a `VisitorHandle`. A non-Activity `Context` gets `FLAG_ACTIVITY_NEW_TASK` automatically. Listener callbacks run on the main thread and are cleared when the activity is destroyed.

## Configuration

| Field | Description |
| --- | --- |
| `url` | HTTPS URL from the console |
| `query` | Business parameters, UTF-8 encoded by the SDK |
| `isApp` | Adds `is_app=1` and shows a close button |
| `title` | Native title; empty uses the page title |
| `language` | `en`, `zh-cn`, `zh-tw`, `ja`, `ko`, `de`, `fr`, `pt`, `ru`, `es`, `vi`, `th`, `id`, `ms`, `tl` |
| `theme` | `light` / `dark` / `system`. Runtime `setTheme` accepts only `light` / `dark` |
| `directChatId` | Opens a conversation (`direct=1&chatid=`) |
| `newMessageSoundMode` | `web` or `native` |

Typed fields (`isApp`, `language`, `theme`, `directChatId`) override the same keys in `query`. Do not put `is_app` / `lang` / `theme` / `direct` / `chatid` in `query`.

## Query parameters

`query` is an opaque map: the SDK only encodes it. Anonymous visitors can omit it.

```kotlin
query = mapOf(
    "sbs" to "user-123",
    "sbs_mm" to signature,
    "ranstr" to randomStr,
    "name" to "Zhang San",
)
```

| Parameter | Required | Meaning |
| --- | --- | --- |
| `sbs` | When binding a user | Unique business-user id; the visitor page uses it to identify a logged-in customer |
| `sbs_mm` | If `sbs` is set | Signature of `sbs` |
| `ranstr` | If signing | Random string used in the signature |
| `name` | Optional | Visitor name shown in the agent console |
| `nickname` | Optional | Visitor nickname |
| `email` | Optional | Visitor email |
| `phone` | Optional | Visitor phone |
| `customer_remark` | Optional | Remark sent with login |
| `ext_fk_id` | Optional | Existing visitor id, to resume that visitor's history |
| `referer` | Optional | Source page URL (also accepts `referer_url`) |
| `source_title` | Optional | Source page title |

Do not send `visitor_id` or `source=android`. Mark an in-app open with `isApp = true`.

## API

| Method | Description |
| --- | --- |
| `TwtVisitorSdk.start(context[, config[, listener]])` | Open a session |
| `handle.setTheme("light" \| "dark")` | Change theme without reload |
| `handle.reportDownloadStatus(status)` | Report download `started` / `completed` / `failed` / `cancelled` |
| `handle.close()` | Finish the activity |
| `TwtVisitorSdk.clearSiteData(url, callback)` | Clear WebView data for the host |

`clearSiteData` returns `SUCCESS`, `INVALID_URL`, `UNSUPPORTED`, `IN_USE`, or `FAILED`. Switch accounts with: `close` → wait for `onDestroy` → clear → open the next account.

## Behavior

- Same-origin HTTPS stays in the WebView; cross-origin HTTPS opens in the system browser; other schemes are blocked.
- Same-origin HTTPS microphone and camera capture can request system permission. Cross-origin requests are denied.
- Cookies, Local Storage, and IndexedDB persist. Third-party cookies and mixed content are off.
- File inputs use the system picker (single or multiple).
- Downloads from the WebView and from the page are both delivered to `onDownloadRequested`. Mark unfinished downloads `failed` when the page is destroyed.
- Back is offered to the page first, then `onBack()`, then the activity closes.

## Links

- [Maven Central](https://central.sonatype.com/artifact/io.github.twt-chat/visitor-sdk)
- [iOS SDK](https://github.com/TWT-Chat/twt-visitor-ios)
- [Flutter plugin](https://github.com/TWT-Chat/visitor_flutter)
