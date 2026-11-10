// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// 手机播放外壳：视频层铺满剩余空间，16:9 画面完整居中（上下暗色渐变填充，不裁画面不变形）；
// 顶部集数条、右侧竖排真实操作、底部常驻展开选集宫格。
// 点「全屏」只隐藏状态栏与叠加层，绝不强制旋转，横屏交给用户自己转手机。

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Portrait playback shell. The host keeps the existing PlayerView instance untouched. */
internal class PhonePlaybackShell(context: Context) : LinearLayout(context) {
    val playerHost = FrameLayout(context)
    val videoFrame = FrameLayout(context)
    val infoPanel = LinearLayout(context)
    private val episodeStrip = PhoneEpisodeStrip(context)
    private val swipeHint = TextView(context)
    private val topBar = LinearLayout(context)
    private val sideActions = LinearLayout(context)
    private val episodeLabel = TextView(context)
    private val portraitHeight = resources.displayMetrics.widthPixels * 9 / 16
    private val infoHeight = (resources.displayMetrics.heightPixels * .34f).toInt()
    private var fullscreen = false
    private var countProvider: () -> Int = { 0 }
    private var currentProvider: () -> Int = { 0 }

    var onNext: (() -> Unit)? = null
    var onFullscreen: ((Boolean) -> Unit)? = null

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun action(label: String, click: () -> Unit) =
        TextView(context).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            minHeight = dp(48)
            minWidth = dp(72)
            isClickable = true
            setTextColor(TvStyle.text)
            background = TvStyle.card(context, false)
            setOnClickListener { click() }
        }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.BLACK)
        playerHost.background =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(255, 12, 22, 30),
                    Color.argb(255, 28, 48, 60),
                    Color.argb(255, 8, 14, 20),
                ),
            )
        videoFrame.background = TvStyle.shape(context, Color.BLACK)
        playerHost.addView(
            videoFrame,
            FrameLayout.LayoutParams(-1, portraitHeight, Gravity.CENTER),
        )
        swipeHint.apply {
            textSize = 13f
            setTextColor(TvStyle.text)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = TvStyle.shape(context, Color.argb(220, 30, 42, 60), TvStyle.accent, 8)
            visibility = View.GONE
        }
        playerHost.addView(swipeHint, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        // 顶部集数条：只显示真实状态，不放假的倍速 / 三点按钮。
        topBar.apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(170, 8, 14, 20))
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        episodeLabel.apply {
            text = "选集"
            textSize = 14f
            setTextColor(TvStyle.text)
        }
        topBar.addView(episodeLabel, LinearLayout.LayoutParams(-1, -2))
        playerHost.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // 右侧竖排：下一集 / 全屏，都是真实可用操作。
        sideActions.apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(10), dp(6))
        }
        sideActions.addView(
            action("下一集") { onNext?.invoke() },
            LinearLayout.LayoutParams(-2, dp(48)).apply { bottomMargin = dp(10) },
        )
        sideActions.addView(
            action("全屏") { onFullscreen?.invoke(!fullscreen) },
            LinearLayout.LayoutParams(-2, dp(48)),
        )
        playerHost.addView(
            sideActions,
            FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL),
        )
        addView(playerHost, LayoutParams(-1, 0, 1f))

        infoPanel.apply {
            orientation = VERTICAL
            setBackgroundColor(TvStyle.background)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        infoPanel.addView(
            TextView(context).apply {
                text = "播放中 · 全屏时上滑切换下一集"
                textSize = 12f
                setTextColor(TvStyle.muted)
            },
            LinearLayout.LayoutParams(-1, -2),
        )
        infoPanel.addView(
            ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(episodeStrip, FrameLayout.LayoutParams(-1, -2))
            },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) },
        )
        addView(infoPanel, LayoutParams(-1, infoHeight))
    }

    /** Episodes stay expanded; clicking a number switches immediately, no dialog. */
    fun bindEpisodes(count: () -> Int, current: () -> Int, select: (Int) -> Unit) {
        countProvider = count
        currentProvider = current
        episodeStrip.bind(count, current, select)
        updateEpisodeLabel()
    }

    fun refreshEpisodes() {
        episodeStrip.refresh()
        updateEpisodeLabel()
    }

    private fun updateEpisodeLabel() {
        val total = countProvider()
        episodeLabel.text =
            if (total > 0) "第 ${currentProvider() + 1} 集  ·  共 $total 集" else "选集"
    }

    fun setFullscreen(on: Boolean) {
        fullscreen = on
        val chromeVisibility = if (on) View.GONE else View.VISIBLE
        infoPanel.visibility = chromeVisibility
        topBar.visibility = chromeVisibility
        sideActions.visibility = chromeVisibility
        if (!on) showHint(null)
    }

    fun isFullscreen() = fullscreen

    fun showHint(text: String?) {
        swipeHint.text = text.orEmpty()
        swipeHint.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }
}
