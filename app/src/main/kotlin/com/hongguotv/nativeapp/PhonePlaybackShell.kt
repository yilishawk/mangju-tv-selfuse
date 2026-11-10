// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// 手机播放外壳：进入播放页即全屏（隐藏状态栏、视频层 MATCH_PARENT），
// 缩放由 MainActivity.syncPhoneResize 按片源宽高决定：竖版 ZOOM 铺满、横版 FIT 居中。
// 选集不再常驻展开：底部只留一条「选集」栏，点它才弹出底部选集弹层；
// 点画面 / 点遮罩 / 点弹层标题都会自动收起。上滑切下一集始终可用。
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
    private val episodeStrip = PhoneEpisodeStrip(context)
    private val swipeHint = TextView(context)
    private val topBar = LinearLayout(context)
    private val sideActions = LinearLayout(context)
    private val episodeLabel = TextView(context)
    private val episodeBar = LinearLayout(context)
    private val episodeSheet = LinearLayout(context)
    private val scrim = View(context)
    private val immersiveButton: TextView
    private val sheetHeight = (resources.displayMetrics.heightPixels * .58f).toInt()
    private var chromeHidden = false
    private var episodesShown = false
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
        // 视频层铺满整个 shell（FrameLayout 叠加），缩放模式由片源比例决定。
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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(170, 8, 14, 20))
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        topBar.addView(
            TextView(context).apply {
                text = "上滑切换下一集"
                textSize = 14f
                setTextColor(TvStyle.text)
            },
            LinearLayout.LayoutParams(-1, -2),
        )
        playerHost.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // 右侧竖排：下一集 / 沉浸，都是真实可用操作。sideActions 自己不隐藏，避免锁死。
        sideActions.apply {
            orientation = LinearLayout.VERTICAL
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

        // 底部常驻「选集」条：只有一行文字，点它才展开选集列表。
        episodeBar.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(205, 8, 14, 20))
            setPadding(dp(14), dp(10), dp(14), dp(12))
            isClickable = true
            setOnClickListener { toggleEpisodes() }
        }
        episodeBar.addView(
            TextView(context).apply {
                text = "播放中 · 上滑切换下一集"
                textSize = 12f
                setTextColor(TvStyle.muted)
            },
            LinearLayout.LayoutParams(-1, -2),
        )
        episodeLabel.apply {
            text = "选集"
            textSize = 15f
            setTextColor(TvStyle.text)
            setPadding(0, dp(4), 0, 0)
        }
        episodeBar.addView(episodeLabel, LinearLayout.LayoutParams(-1, -2))
        addView(episodeBar, LayoutParams(-1, -2, Gravity.BOTTOM))

        // 半透明遮罩：展开选集时盖住视频与其它按钮，点任意位置自动收起。
        scrim.apply {
            setBackgroundColor(Color.argb(140, 0, 0, 0))
            visibility = View.GONE
            setOnClickListener { hideEpisodes() }
        }
        addView(scrim, LayoutParams(-1, -1))

        // 底部选集弹层：分段 + 宫格沿用 PhoneEpisodeStrip，默认 GONE。
        episodeSheet.apply {
            orientation = LinearLayout.VERTICAL
            background = TvStyle.shape(context, Color.argb(248, 16, 24, 34), TvStyle.outline, 16)
            setPadding(dp(14), dp(12), dp(14), dp(16))
            visibility = View.GONE
            isClickable = true // 吃掉点击，不穿透到遮罩
        }
        episodeSheet.addView(
            TextView(context).apply {
                text = "选集  ·  点击这里收起"
                textSize = 13f
                setTextColor(TvStyle.muted)
                setPadding(dp(2), 0, dp(2), dp(10))
                setOnClickListener { hideEpisodes() }
            },
            LinearLayout.LayoutParams(-1, -2),
        )
        episodeSheet.addView(
            ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(episodeStrip, FrameLayout.LayoutParams(-1, -2))
            },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        addView(episodeSheet, LayoutParams(-1, sheetHeight, Gravity.BOTTOM))
    }

    /** 只收起顶部条与底部选集栏；视频仍铺满，右侧按钮保留以便恢复。 */
    private fun toggleChrome() {
        chromeHidden = !chromeHidden
        if (chromeHidden) hideEpisodes()
        val visibility = if (chromeHidden) View.GONE else View.VISIBLE
        topBar.visibility = visibility
        episodeBar.visibility = visibility
        immersiveButton.text = if (chromeHidden) "显示操作" else "沉浸"
        if (!chromeHidden) showHint(null)
    }

    private fun toggleEpisodes() {
        if (episodesShown) hideEpisodes() else showEpisodes()
    }

    /** 点底部条展开选集弹层：刷新当前集高亮，并把条的文案切成「点击收起」。 */
    fun showEpisodes() {
        if (chromeHidden || episodesShown) return
        episodesShown = true
        episodeStrip.refresh()
        scrim.visibility = View.VISIBLE
        episodeSheet.visibility = View.VISIBLE
        updateEpisodeLabel()
    }

    /** 点画面 / 点遮罩 / 点弹层标题 / 沉浸时自动收起选集列表。 */
    fun hideEpisodes() {
        if (!episodesShown) return
        episodesShown = false
        scrim.visibility = View.GONE
        episodeSheet.visibility = View.GONE
        updateEpisodeLabel()
    }

    /** 视频恒为全屏铺满态，上滑切下一集始终启用；没有「进入全屏」这一步。 */
    fun isFullscreen() = true

    /** 选集数据绑定；弹层默认收起，点底部条才展开。 */
    fun bindEpisodes(count: () -> Int, current: () -> Int, select: (Int) -> Unit) {
        countProvider = count
        currentProvider = current
        episodeStrip.bind(count, current, select)
        updateEpisodeLabel()
    }

    fun refreshEpisodes() {
        if (episodesShown) episodeStrip.refresh()
        updateEpisodeLabel()
    }

    private fun updateEpisodeLabel() {
        val total = countProvider()
        val tail = if (episodesShown) "点击收起" else "点击展开"
        episodeLabel.text =
            if (total > 0) "选集  ·  第 ${currentProvider() + 1} 集 / 共 $total 集    $tail"
            else "选集"
    }

    fun showHint(text: String?) {
        swipeHint.text = text.orEmpty()
        swipeHint.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }
}
