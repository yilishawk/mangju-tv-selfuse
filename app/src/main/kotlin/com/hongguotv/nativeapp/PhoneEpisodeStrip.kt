// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// 手机播放页底部选集弹层里的宫格：默认随弹层收起，点数字直接切集。
// 分段与手动翻组逻辑保留：userPaged 防止点「下一组」后被当前集所在组覆盖回去。

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Episode grid hosted by the phone playback shell's bottom sheet. */
internal class PhoneEpisodeStrip(context: Context) : LinearLayout(context) {
    private var countProvider: () -> Int = { 0 }
    private var currentProvider: () -> Int = { 0 }
    private var select: (Int) -> Unit = {}
    private var group = 0
    // 用户手动翻过组后，render 不许再用「当前播放集所在组」把 group 覆盖回去。
    // 上一版正是这里导致点「下一组」立刻弹回原组，看起来像按钮没反应。
    private var userPaged = false

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
        userPaged = false
        group = if (count() > 0) current().coerceAtLeast(0) / 20 else 0
        render()
    }

    /** 每次切集刷新高亮。自动连播跨组时清掉手动标记，跟随播放；否则保持用户翻到的组。 */
    fun refresh() {
        val count = countProvider()
        if (count > 0) {
            val current = currentProvider().coerceIn(0, count - 1)
            if (userPaged && (current < group * 20 || current >= (group + 1) * 20))
                userPaged = false
        }
        render()
    }

    private fun render() {
        val count = countProvider()
        if (count <= 0) {
            visibility = View.GONE
            return
        }
        visibility = View.VISIBLE
        val current = currentProvider().coerceIn(0, count - 1)
        if (!userPaged) group = current / 20
        if (group * 20 >= count) group = count / 20

        heading.text = "选集  ·  正在看第 ${current + 1} 集  ·  共 $count 集"
        pager.removeAllViews()
        val previous =
            button("‹ 上一组", group > 0) {
                group--
                userPaged = true
                render()
            }
        val next =
            button("下一组 ›", (group + 1) * 20 < count) {
                group++
                userPaged = true
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
