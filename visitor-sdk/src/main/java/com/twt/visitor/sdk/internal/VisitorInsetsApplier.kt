package com.twt.visitor.sdk.internal

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Adds system bar and display cutout insets to the root container while preserving the original padding. */
internal object VisitorInsetsApplier {
    fun apply(view: View) {
        val original = Insets.of(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            view.paddingBottom,
        )
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val padding = calculatePadding(original, insets)
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /** Computes the final padding after combining the original padding with system bar and cutout insets. */
    fun calculatePadding(original: Insets, insets: WindowInsetsCompat): Insets {
        val safeArea = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        return Insets.of(
            original.left + safeArea.left,
            original.top + safeArea.top,
            original.right + safeArea.right,
            original.bottom + safeArea.bottom,
        )
    }
}
