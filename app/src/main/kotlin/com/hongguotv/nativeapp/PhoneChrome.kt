// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// 手机形态的导航外壳。TV 形态不创建这些视图，主页面保持原样。

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Bottom tab bar for the phone build; maps to the original tab indices 0/1/2/3. */
internal class PhoneChrome(
    context: Context,
    labels: List<String>,
    private val onTab: (Int) -> Unit,
) : LinearLayout(context) {
    private val items = mutableListOf<TextView>()
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    init {
        orientation = HORIZONTAL
        setBackgroundColor(TvStyle.surface)
        setPadding(dp(6), dp(4), dp(6), dp(6))
        labels.forEachIndexed { index, label ->
            val item =
                TextView(context).apply {
                    text = label
                    textSize = 12f
                    gravity = Gravity.CENTER
                    minHeight = dp(48)
                    isClickable = true
                    isFocusable = false
                    setTextColor(TvStyle.muted)
                    setOnClickListener { onTab(index) }
                }
            addView(item, LayoutParams(0, dp(48), 1f))
            items += item
        }
    }

    fun select(index: Int) {
        items.forEachIndexed { i, view ->
            val active = i == index
            view.setTextColor(if (active) TvStyle.accent else TvStyle.muted)
            view.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            view.background =
                if (active) TvStyle.shape(context, TvStyle.raised, TvStyle.accent, 8) else null
        }
    }
}

/** "我的" secondary entries, mapped back to the original tabs 3..6. */
internal class PhoneMyTabs(
    context: Context,
    private val onTab: (Int) -> Unit,
) : HorizontalScrollView(context) {
    private val items = mutableListOf<TextView>()
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    init {
        isHorizontalScrollBarEnabled = false
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        addView(line, FrameLayout.LayoutParams(-2, -2))
        listOf(3 to "收藏", 4 to "观看历史", 5 to "账户预约", 6 to "设置").forEach { (tab, label) ->
            val item =
                TextView(context).apply {
                    text = label
                    textSize = 12f
                    gravity = Gravity.CENTER
                    minHeight = dp(40)
                    setPadding(dp(14), dp(6), dp(14), dp(6))
                    isClickable = true
                    setTextColor(TvStyle.muted)
                    background = TvStyle.card(context, false)
                    setOnClickListener { onTab(tab) }
                }
            line.addView(item, LinearLayout.LayoutParams(-2, dp(40)).apply { rightMargin = dp(8) })
            items += item
        }
    }

    fun select(tab: Int) {
        items.forEachIndexed { i, view ->
            val active = 3 + i == tab
            view.setTextColor(if (active) TvStyle.accent else TvStyle.muted)
            view.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}
