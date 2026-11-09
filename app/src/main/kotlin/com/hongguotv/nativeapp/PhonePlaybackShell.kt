// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// 手机播放外壳：竖屏时上方 16:9 播放器、下方信息区；全屏时隐藏信息区并铺满播放器。

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Portrait playback shell. The host keeps the existing PlayerView instance untouched. */
internal class PhonePlaybackShell(context: Context) : LinearLayout(context) {
    val playerHost = FrameLayout(context)
    val infoPanel = LinearLayout(context)
    private val swipeHint = TextView(context)
    private val portraitHeight = resources.displayMetrics.widthPixels * 9 / 16
    private var fullscreen = false

    var onEpisodes: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onFullscreen: ((Boolean) -> Unit)? = null

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun action(label: String, click: () -> Unit) =
        TextView(context).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            minHeight = dp(48)
            isClickable = true
            setTextColor(TvStyle.text)
            background = TvStyle.card(context, false)
            setOnClickListener { click() }
        }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.BLACK)
        addView(playerHost, LayoutParams(-1, portraitHeight))
        swipeHint.apply {
            textSize = 13f
            setTextColor(TvStyle.text)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = TvStyle.shape(context, Color.argb(220, 30, 42, 60), TvStyle.accent, 8)
            visibility = View.GONE
        }
        playerHost.addView(swipeHint, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        infoPanel.apply {
            orientation = VERTICAL
            setBackgroundColor(TvStyle.background)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        infoPanel.addView(
            TextView(context).apply {
                text = "播放中 · 点全屏可横屏，全屏时上滑切换下一集"
                textSize = 12f
                setTextColor(TvStyle.muted)
            },
            LinearLayout.LayoutParams(-1, -2),
        )
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        listOf(
            "选集" to { onEpisodes?.invoke() },
            "下一集" to { onNext?.invoke() },
            "全屏" to { onFullscreen?.invoke(!fullscreen) },
        ).forEach { (label, click) ->
            actions.addView(
                action(label, click),
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) },
            )
        }
        infoPanel.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        addView(infoPanel, LayoutParams(-1, 0, 1f))
    }

    fun setFullscreen(on: Boolean) {
        fullscreen = on
        infoPanel.visibility = if (on) View.GONE else View.VISIBLE
        val lp = playerHost.layoutParams as LayoutParams
        if (on) {
            lp.height = 0
            lp.weight = 1f
        } else {
            lp.height = portraitHeight
            lp.weight = 0f
        }
        playerHost.layoutParams = lp
        if (!on) showHint(null)
    }

    fun isFullscreen() = fullscreen

    fun showHint(text: String?) {
        swipeHint.text = text.orEmpty()
        swipeHint.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }
}
