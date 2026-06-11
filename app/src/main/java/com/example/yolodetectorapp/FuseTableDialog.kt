package com.example.yolodetectorapp

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*

class FuseTableDialog(
    context: Context,
    private val detections: List<Detection>
) : Dialog(context) {

    private val classTextColors = mapOf(
        "10_amp"  to Color.parseColor("#E8453C"),
        "15_amp"  to Color.parseColor("#E07B20"),
        "20_amp"  to Color.parseColor("#C9970A"),
        "25_amp"  to Color.parseColor("#0FA8CC"),
        "2_amp"   to Color.parseColor("#0DAD7A"),
        "30_amp"  to Color.parseColor("#2E7DD6"),
        "3_amp"   to Color.parseColor("#3DAD3A"),
        "5_amp"   to Color.parseColor("#7B4FD6"),
        "7.5_amp" to Color.parseColor("#D46320"),
        "empty"   to Color.parseColor("#555570")
    )

    private val j3Labels = listOf("F10","F11","F12","F13","F14","F15","F16","F17","TEST")

    private val BG_DIALOG   = Color.parseColor("#E0101020")
    private val BG_HEADER   = Color.parseColor("#28FFFFFF")
    private val BG_ROW_ODD  = Color.parseColor("#14FFFFFF")
    private val BG_ROW_EVN  = Color.parseColor("#00000000")
    private val COL_DIVIDER = Color.parseColor("#44FFFFFF")
    private val COL_POS     = Color.parseColor("#88AABBCC")
    private val COL_HEADER  = Color.parseColor("#AABBCC")

    private val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600

    private val LABEL_W_DP = if (isTablet) 52 else 38
    private val ROW_H_DP   = if (isTablet) 44 else 30
    private val HDR_H_DP   = if (isTablet) 52 else 36
    private val CLOSE_H_DP = if (isTablet) 56 else 44

    private val TEXT_LABEL = if (isTablet) 14f else 10f
    private val TEXT_VALUE = if (isTablet) 16f else 12f
    private val TEXT_HDR   = if (isTablet) 15f else 11f
    private val TEXT_CLOSE = if (isTablet) 18f else 13f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val (j2, j3) = splitColumns(detections)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(BG_DIALOG)
                val r = dp(12).toFloat()
                cornerRadii = if (isTablet)
                    floatArrayOf(r,r, r,r, r,r, r,r)
                else
                    floatArrayOf(r,r, r,r, 0f,0f, 0f,0f)
            }
        }

        root.addView(buildHeaderRow())
        root.addView(dividerLine())

        for (i in 0 until 9) {
            root.addView(buildDataRow(
                label1 = "F${i + 1}",
                value1 = j2.getOrNull(i)?.className ?: "—",
                label2 = j3Labels[i],
                value2 = j3.getOrNull(i)?.className ?: "—",
                shaded = i % 2 == 0
            ))
        }

        root.addView(dividerLine())

        root.addView(Button(context).apply {
            text = "KAPAT"
            setTextColor(Color.parseColor("#CC4444"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = TEXT_CLOSE
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(CLOSE_H_DP)
            )
            setOnClickListener { dismiss() }
        })

        setContentView(root)

        val dialogWidth = if (isTablet) dp(640) else WindowManager.LayoutParams.MATCH_PARENT
        window?.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        window?.attributes = window?.attributes?.also { a ->
            a.gravity = if (isTablet) Gravity.CENTER else Gravity.BOTTOM
            a.y = 0
        }
    }

    private fun buildHeaderRow(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(BG_HEADER)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(spacer(LABEL_W_DP, HDR_H_DP))
        addView(headerText("◄ SOL (J2)", weight = 1f))
        addView(verticalDivider(HDR_H_DP))
        addView(spacer(LABEL_W_DP, HDR_H_DP))
        addView(headerText("SAĞ (J3) ►", weight = 1f))
    }

    private fun buildDataRow(
        label1: String, value1: String,
        label2: String, value2: String,
        shaded: Boolean
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(if (shaded) BG_ROW_ODD else BG_ROW_EVN)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(labelText(label1))
        addView(valueText(value1, weight = 1f))
        addView(verticalDivider(ROW_H_DP))
        addView(labelText(label2))
        addView(valueText(value2, weight = 1f))
    }

    private fun labelText(text: String) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_LABEL)
        setTextColor(COL_POS)
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(LABEL_W_DP), dp(ROW_H_DP))
    }

    private fun valueText(value: String, weight: Float): TextView {
        val isEmpty = value == "—" || value == "empty"
        val color = when {
            value == "—" -> Color.parseColor("#445566")
            else         -> classTextColors[value] ?: Color.parseColor("#AAAACC")
        }
        return TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_VALUE)
            typeface = if (isEmpty) Typeface.defaultFromStyle(Typeface.ITALIC)
            else Typeface.DEFAULT_BOLD
            setTextColor(color)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(ROW_H_DP), weight)
        }
    }

    private fun headerText(text: String, weight: Float) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_HDR)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(COL_HEADER)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(HDR_H_DP), weight)
    }

    private fun dividerLine() = View(context).apply {
        setBackgroundColor(COL_DIVIDER)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        )
    }

    private fun verticalDivider(heightDp: Int) = View(context).apply {
        setBackgroundColor(COL_DIVIDER)
        layoutParams = LinearLayout.LayoutParams(dp(1), dp(heightDp))
    }

    private fun spacer(widthDp: Int, heightDp: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(heightDp))
    }

    private fun splitColumns(dets: List<Detection>): Pair<List<Detection>, List<Detection>> {
        if (dets.isEmpty()) return Pair(emptyList(), emptyList())
        val sorted = dets.sortedBy { (it.box.left + it.box.right) / 2f }
        val midX   = sorted.map { (it.box.left + it.box.right) / 2f }.average().toFloat()
        val left  = sorted.filter { (it.box.left + it.box.right) / 2f < midX }
            .sortedBy { (it.box.top + it.box.bottom) / 2f }
        val right = sorted.filter { (it.box.left + it.box.right) / 2f >= midX }
            .sortedBy { (it.box.top + it.box.bottom) / 2f }
        return Pair(left, right)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}