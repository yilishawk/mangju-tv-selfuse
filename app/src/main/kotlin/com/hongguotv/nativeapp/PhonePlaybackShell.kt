// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// 手机播放外壳：进入播放页即全屏铺满（16:9 片源由 MainActivity 设 ZOOM 等比裁切），
// 不再只有屏幕中间一条，也不需要点「全屏」。
// 顶部集数条、右侧竖排、底部常驻展开选集都是叠在视频上的半透明层。
// 右侧「沉浸」只收起顶部条与选集，视频始终铺满；上滑切下一集始终可用。
// 横屏完全交给用户自己转手机，这里不碰 requestedOrientation。

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Fullscreen portrait playback shell. The host keeps the existing PlayerView instance untouched. */
internal class PhonePlaybackShell(context: Context) : FrameLayout(context) {
    val playerHost = FrameLayout(context)
    val videoFrame = FrameLayout(context)
    val infoPanel = LinearLayout(context)
    private val episodeStrip = PhoneEpisodeStrip(context)
    private val swipeHint = TextView(context)
    private val topBar = LinearLayout(context)
    private val sideActions = LinearLayout(context)
    private val episodeLabel = TextView(context)
    private val immersiveButton: TextView
    private val infoHeight = (resources.displayMetrics.heightPixels * .34f).toInt()
    private var chromeHidden = false
    private var countProvider: () -> Int = { 0 }
    private var currentProvider: () -> Int = { 0 }

    var onNext: (() -> Unit)? = null

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
        setBackgroundColor(Color.BLACK)
        // 视频层铺满整个 shell（FrameLayout 叠加），不再切成上视频 / 下信息两块。
        addView(playerHost, LayoutParams(-1, -1))
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
        playerHost.addView(videoFrame, FrameLayout.LayoutParams(-1, -1))

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

        // 右侧竖排：下一集 / 沉浸，都是真实可用操作。sideActions 自己不隐藏，避免锁死。
        sideActions.apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(10), dp(6))
        }
        sideActions.addView(
            action("下一集") { onNext?.invoke() },
            LinearLayout.LayoutParams(-2, dp(48)).apply { bottomMargin = dp(10) },
        )
        immersiveButton = action("沉浸") { toggleChrome() }
        sideActions.addView(immersiveButton, LinearLayout.LayoutParams(-2, dp(48)))
        playerHost.addView(
            sideActions,
            FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL),
        )

        // 底部常驻展开选集：半透明叠在视频上，不占独立分块。
        infoPanel.apply {
            orientation = VERTICAL
            setBackgroundColor(Color.argb(205, 8, 14, 20))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        infoPanel.addView(
            TextView(context).apply {
                text = "播放中 · 上滑切换下一集"
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
        addView(infoPanel, LayoutParams(-1, infoHeight, Gravity.BOTTOM))
    }

    /** 只收起顶部条与选集；视频仍铺满，右侧按钮保留以便恢复。 */
    private fun toggleChrome() {
        chromeHidden = !chromeHidden
        val visibility = if (chromeHidden) View.GONE else View.VISIBLE
        infoPanel.visibility = visibility
        topBar.visibility = visibility
        immersiveButton.text = if (chromeHidden) "显示操作" else "沉浸"
        if (!chromeHidden) showHint(null)
    }

    /** 视频恒为全屏铺满态，上滑切下一集始终启用；没有「进入全屏」这一步。 */
    fun isFullscreen() = true

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

    fun showHint(text: String?) {
        swipeHint.text = text.orEmpty()
        swipeHint.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }
}
