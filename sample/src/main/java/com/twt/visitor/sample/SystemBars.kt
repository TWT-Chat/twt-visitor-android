package com.twt.visitor.sample

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * targetSdk 35+ forces edge-to-edge, so the sample adds the system bar insets back to keep the normal status bar / navigation bar / content layering.
 */
internal fun View.applySystemBarInsets() {
    val original = listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        view.setPadding(
            original[0] + bars.left,
            original[1] + bars.top,
            original[2] + bars.right,
            original[3] + bars.bottom,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
