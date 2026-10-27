package com.twt.visitor.sample

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * targetSdk 35+ 强制 edge-to-edge，示例自己把系统栏避让补回来，保持「状态栏 + 导航栏 + 内容」的正常层级。
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
