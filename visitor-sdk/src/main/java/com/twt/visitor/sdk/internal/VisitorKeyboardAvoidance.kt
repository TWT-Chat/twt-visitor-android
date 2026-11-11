package com.twt.visitor.sdk.internal

import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Shrinks the container to the area above the soft keyboard.
 *
 * A page that stays under the keyboard keeps a layout viewport the keyboard covers, so WebKit pans the
 * whole page up to reveal the focused input: the page header scrolls out of view and blank space shows
 * up above the keyboard. Shrinking the container instead keeps the page's own height correct.
 */
internal object VisitorKeyboardAvoidance {
    /** Adds an adjustable bottom inset to [container]; it stays 0 when the host already made room. */
    fun apply(container: View) {
        // On API < 30 SOFT_INPUT_ADJUST_RESIZE resizes the window itself, so padding here would subtract twice.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val originalBottom = container.paddingBottom
        var imeBottom = 0
        // Shrinking in one step the moment the keyboard starts opens a blank band until the keyboard arrives, so the
        // shrink follows the keyboard edge frame by frame instead.
        var fromOverlap = 0
        var toOverlap = 0
        var animating = false
        var hiding = false
        // A new inset can arrive before its animation starts; that ordering, not the current visibility, tells the way.
        var insetsArrivedFirst = false

        fun setOverlap(target: View, overlap: Int) {
            val bottom = originalBottom + overlap
            if (target.paddingBottom == bottom) return
            target.setPadding(target.paddingLeft, target.paddingTop, target.paddingRight, bottom)
        }

        ViewCompat.setOnApplyWindowInsetsListener(container) { target, insets ->
            imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            // While the keyboard hides, its insets still report the old height until the animation ends.
            if (!(animating && hiding)) {
                val overlap = overlapPx(target, imeBottom)
                if (overlap != toOverlap) {
                    fromOverlap = target.paddingBottom - originalBottom
                    toOverlap = overlap
                    insetsArrivedFirst = true
                    // An animation can start right after this dispatch: let it take over, apply directly only when none comes.
                    if (!animating) target.post { if (!animating) setOverlap(target, toOverlap) }
                }
            }
            insets
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            container,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    animating = true
                    if (insetsArrivedFirst) {
                        // The inset was applied before its animation started; rewind so the animation drives it from there.
                        insetsArrivedFirst = false
                        hiding = false
                        setOverlap(container, fromOverlap)
                        return
                    }
                    // A hiding keyboard dispatches no new inset until the animation ends: aim at 0 now, or the padding
                    // stays put while the keyboard slides away and snaps back in one step afterwards.
                    hiding = ViewCompat.getRootWindowInsets(container)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    if (hiding) {
                        fromOverlap = container.paddingBottom - originalBottom
                        toOverlap = 0
                    }
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat {
                    val ime = runningAnimations.firstOrNull { it.typeMask and WindowInsetsCompat.Type.ime() != 0 }
                    if (ime != null) {
                        val overlap = (fromOverlap + (toOverlap - fromOverlap) * ime.interpolatedFraction).toInt()
                        setOverlap(container, overlap)
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    animating = false
                    hiding = false
                    insetsArrivedFirst = false
                    setOverlap(container, toOverlap)
                }
            },
        )
        // A host can move this container without a new inset (Flutter resizes its platform view), so recheck on layout.
        container.addOnLayoutChangeListener { target, _, _, _, _, _, _, _, _ ->
            if (animating) return@addOnLayoutChangeListener
            toOverlap = overlapPx(target, imeBottom)
            setOverlap(target, toOverlap)
        }
        ViewCompat.requestApplyInsets(container)
    }

    /** Part of the keyboard that still covers [view], in pixels; 0 when the host already shrank it. */
    private fun overlapPx(view: View, imeBottom: Int): Int {
        if (imeBottom <= 0 || view.height == 0) return 0
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val keyboardTop = view.rootView.height - imeBottom
        return (location[1] + view.height - keyboardTop).coerceAtLeast(0)
    }
}
