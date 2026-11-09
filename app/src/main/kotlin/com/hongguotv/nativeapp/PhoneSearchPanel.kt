// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// 手机形态的单列搜索页：系统软键盘 + 最近搜索 + 单列结果容器。
// 与 TvSearchPanel 平行的另一套 View 组装，业务回调仍是 MainActivity 的 runSearch。

import android.content.Context
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Touch-first search page; the TV three-column remote panel is not created on phones. */
internal class PhoneSearchPanel(
    context: Context,
    initial: String,
    history: List<String>,
    private val search: (String) -> Unit,
    private val back: () -> Unit,
) : LinearLayout(context) {
    val input: EditText
    val searchButton: TextView
    val results = LinearLayout(context).apply { orientation = VERTICAL }
    var resultEntry: (() -> View?)? = null

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun chip(value: String) =
        TextView(context).apply {
            text = value
            textSize = 12f
            setTextColor(TvStyle.text)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = TvStyle.card(context, false)
            isClickable = true
            setOnClickListener { submit(value) }
        }

    private fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun submit(value: String = input.text.toString()) {
        val q = value.trim()
        if (q.isEmpty()) {
            back()
            return
        }
        hideKeyboard()
        search(q)
    }

    init {
        orientation = VERTICAL
        val bar = LinearLayout(context).apply { orientation = HORIZONTAL }
        input =
            EditText(context).apply {
                id = View.generateViewId()
                hint = "剧名、关键词或拼音"
                setText(initial)
                setSelection(text.length)
                textSize = 15f
                setTextColor(TvStyle.text)
                setHintTextColor(TvStyle.muted)
                isSingleLine = true
                filters = arrayOf(InputFilter.LengthFilter(80))
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                background = TvStyle.card(context, false)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnEditorActionListener { _, _, _ ->
                    submit()
                    true
                }
            }
        bar.addView(input, LayoutParams(0, dp(46), 1f))
        searchButton =
            TextView(context).apply {
                text = "搜索"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(TvStyle.text)
                background = TvStyle.card(context, false)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                isClickable = true
                setOnClickListener { submit() }
            }
        bar.addView(searchButton, LayoutParams(-2, dp(46)).apply { leftMargin = dp(8) })
        addView(bar, LayoutParams(-1, -2))

        if (history.isNotEmpty()) {
            addView(
                TextView(context).apply {
                    text = "最近搜索"
                    textSize = 12f
                    setTextColor(TvStyle.muted)
                    setPadding(0, dp(12), 0, dp(6))
                },
                LayoutParams(-2, -2),
            )
            val strip = LinearLayout(context).apply { orientation = HORIZONTAL }
            history.distinct().take(8).forEach { word ->
                strip.addView(chip(word), LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            }
            addView(
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(strip)
                },
                LayoutParams(-1, -2),
            )
        }
        addView(results, LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })
    }

    fun entry(): View = input

    // 不自定义 findFocus()：继承 ViewGroup 的实现即可，与 TvSearchPanel 的语义保持一致。

    fun middleEntry(): View? = null

    fun updateNavigation() = Unit
}
