// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// 全屏横屏时挂在 PlayerView 上的上滑切集手势；竖屏内嵌播放不启用，交给页面滚动。

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** Swipe up to play the next episode; only active while the phone player is fullscreen. */
internal class PhoneSwipeGesture(
    context: Context,
    private val enabled: () -> Boolean,
    private val onNext: () -> Unit,
    private val onHint: (String?) -> Unit,
) : View.OnTouchListener {
    private val threshold = 120f * context.resources.displayMetrics.density
    private val touchSlop = 12f * context.resources.displayMetrics.density
    private var startX = 0f
    private var startY = 0f
    private var tracking = false
    private var lastTriggerAt = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!enabled()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                tracking = true
                // 拿住完整事件流；UP 时若判定为短点击，用 performClick 补回点击语义。
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val dy = event.y - startY
                val dx = event.x - startX
                onHint(
                    if (dy < 0 && abs(dy) >= abs(dx) * 1.2f) {
                        if (abs(dy) >= threshold) "松开切换下一集" else "继续上滑切换下一集"
                    } else null
                )
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                val dy = event.y - startY
                val dx = event.x - startX
                tracking = false
                onHint(null)
                val hit = dy <= -threshold && abs(dy) >= abs(dx) * 1.2f
                if (hit) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastTriggerAt >= 900L) {
                        lastTriggerAt = now
                        onNext()
                    }
                } else if (abs(dx) < touchSlop && abs(dy) < touchSlop) {
                    v.performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                onHint(null)
                return true
            }
        }
        return false
    }
}
