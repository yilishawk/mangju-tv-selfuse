// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// 手机播放页常驻展开的选集宫格：不弹 Dialog，点数字直接切集。

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Always-expanded episode grid for the phone playback shell. */
internal class PhoneEpisodeStrip(context: Context) : LinearLayout(context) {
    private var countProvider: () -> Int = { 0 }
    private var currentProvider: () -> Int = { 0 }
    private var select: (Int) -> Unit = {}
    private var group = 0
    private var lastCurrentGroup = -1

    private val heading = TextView(context)
    private val pager = LinearLayout(context)
    private val grid = LinearLayout(context)

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float = 13f) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(TvStyle.text)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

    private fun button(value: String, enabled: Boolean, action: () -> Unit) =
        label(value).apply {
            minHeight = dp(40)
            isClickable = enabled
            isEnabled = enabled
            alpha = if (enabled) 1f else .4f
            background = TvStyle.card(context, false)
            if (enabled) setOnClickListener { action() }
        }

    init {
        orientation = VERTICAL
        heading.apply {
            textSize = 15f
            setTextColor(TvStyle.text)
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        addView(heading, LayoutParams(-1, -2))
        pager.orientation = HORIZONTAL
        pager.gravity = Gravity.CENTER_VERTICAL
        addView(pager, LayoutParams(-1, -2))
        grid.orientation = VERTICAL
        addView(grid, LayoutParams(-1, -2))
    }

    fun bind(count: () -> Int, current: () -> Int, onSelect: (Int) -> Unit) {
        countProvider = count
        currentProvider = current
        select = onSelect
        render()
    }

    /** Called on every episode transition so the highlighted cell and group follow playback. */
    fun refresh() = render()

    private fun render() {
        val count = countProvider()
        if (count <= 0) {
            visibility = View.GONE
            return
        }
        visibility = View.VISIBLE
        val current = currentProvider().coerceIn(0, count - 1)
        val wantedGroup = current / 20
        if (lastCurrentGroup != wantedGroup) {
            group = wantedGroup
            lastCurrentGroup = wantedGroup
        }
        if (group * 20 >= count) group = count / 20

        heading.text = "选集  ·  正在看第 ${current + 1} 集  ·  共 $count 集"
        pager.removeAllViews()
        val previous =
            button("‹ 上一组", group > 0) {
                group--
                lastCurrentGroup = -1
                render()
            }
        val next =
            button("下一组 ›", (group + 1) * 20 < count) {
                group++
                lastCurrentGroup = -1
                render()
            }
        val range = label("第 ${group * 20 + 1}—${minOf((group + 1) * 20, count)} 集", 12f)
        pager.addView(previous, LayoutParams(0, -2, 1f))
        pager.addView(range, LayoutParams(0, -2, 1.4f))
        pager.addView(next, LayoutParams(0, -2, 1f))

        grid.removeAllViews()
        (group * 20 until minOf((group + 1) * 20, count)).chunked(5).forEach { indexes ->
            val line = LinearLayout(context).apply { orientation = HORIZONTAL }
            indexes.forEach { index ->
                val cell =
                    label("${index + 1}", 14f).apply {
                        minHeight = dp(44)
                        setTextColor(if (index == current) TvStyle.accent else TvStyle.text)
                        background = TvStyle.card(context, false, index == current)
                        isClickable = true
                        contentDescription = "第 ${index + 1} 集"
                        setOnClickListener { select(index) }
                    }
                line.addView(
                    cell,
                    LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) },
                )
            }
            repeat(5 - indexes.size) {
                line.addView(View(context), LayoutParams(0, 1, 1f))
            }
            grid.addView(line, LayoutParams(-1, -2))
        }
    }
}
