package com.twt.visitor.sdk.internal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback

/** Parses Android file chooser results, covering single selection, multiple selection and cancellation. */
internal object FileChooserResultParser {
    fun parse(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK || data == null) return null

        val uris = linkedSetOf<Uri>()
        data.data?.let(uris::add)
        data.clipData?.let { clipData ->
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index).uri?.let(uris::add)
            }
        }
        return uris.takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}

/** Guarantees the file chooser callback completes at most once and cancels the previous request when a new one arrives. */
internal class PendingFileChooser {
    private var callback: ValueCallback<Array<Uri>>? = null

    fun replace(next: ValueCallback<Array<Uri>>) {
        complete(null)
        callback = next
    }

    fun complete(value: Array<Uri>?) {
        val current = callback ?: return
        callback = null
        current.onReceiveValue(value)
    }
}
