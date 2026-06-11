package com.example.yolodetectorapp

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*

class FuseTableDialog(
    context: Context,
    private val detections: List<Detection>
) : Dialog(context) {

    // ── Sigorta renkleri ────────────────────────────────────────────
    private val classTextColors = mapOf(
        "10_amp"  to Color.parseColor("#E8453C"),
        "15_amp"  to Color.parseColor("#E07B20"),
        "20_amp"  to Color.parseColor("#C9970A"),
        "25_amp"  to Color.parseColor("#0FA8CC"),
        "2_amp"   to Color.parseColor("#0DAD7A"),
        "30_amp"  to Color.parseColor("#2E7DD6"),
        "5_amp"   to Color.parseColor("#7B4FD6"),
        "7.5_amp" to Color.parseColor("#D46320"),
        "empty"   to Color.parseColor("#555570")
    )

    private val j3Labels = listOf("F10","F11","F12","F13","F14","F15","F16","F17","TEST")

    // ── Renkler ──────────────────────────────────────────────────────
    private val BG_DIALOG   = Color.parseColor("#E0101020")
    private val BG_HEADER   = Color.parseColor("#28FFFFFF")
    private val BG_ROW_ODD  = Color.parseColor("#14FFFFFF")
    private val BG_ROW_EVN  = Color.parseColor("#00000000")
    private val COL_DIVIDER = Color.parseColor("#44FFFFFF")
    private val COL_POS     = Color.parseColor("#88AABBCC")
    private val COL_HEADER  = Color.parseColor("#AABBCC")

    // Sütun genişlikleri dp cinsinden
    private val LABEL_W_DP = 38   // F1, F10 etiketi için sabit genişlik
    private val ROW_H_DP   = 30   // her satır yüksekliği
    private val HDR_H_DP   = 36   // başlık yüksekliği

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val (j2, j3) = splitColumns(detections)

        // ── Dış kap ──────────────────────────────────────────────────
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(BG_DIALOG)
                val r = dp(12).toFloat()
                cornerRadii = floatArrayOf(r,r, r,r, 0f,0f, 0f,0f)
            }
        }

        // ── Başlık satırı ─────────────────────────────────────────────
        // Yapı: [LABEL_W boşluk] [SOL(J2) weight=1] [1dp divider] [LABEL_W boşluk] [SAĞ(J3) weight=1]
        root.addView(buildHeaderRow())
        root.addView(dividerLine())

        // ── 9 veri satırı ─────────────────────────────────────────────
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

        // ── Kapat butonu ──────────────────────────────────────────────
        root.addView(Button(context).apply {
            text = "KAPAT"
            setTextColor(Color.parseColor("#CC4444"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
            )
            setOnClickListener { dismiss() }
        })

        setContentView(root)

        window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.attributes = window?.attributes?.also { a ->
            a.gravity = Gravity.BOTTOM
            a.y = 0
        }
    }

    // Başlık satırı
    private fun buildHeaderRow(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(BG_HEADER)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        // Sol taraf etiket boşluğu (F1..F9 ile hizalanır)
        addView(spacer(LABEL_W_DP, HDR_H_DP))

        // "◄ SOL (J2)" — value sütununu tamamen kaplar
        addView(headerText("◄ SOL (J2)", weight = 1f))

        // Dikey ayırıcı
        addView(verticalDivider(HDR_H_DP))

        // Sağ taraf etiket boşluğu (F10..TEST ile hizalanır)
        addView(spacer(LABEL_W_DP, HDR_H_DP))

        // "SAĞ (J3) ►" — value sütununu tamamen kaplar
        addView(headerText("SAĞ (J3) ►", weight = 1f))
    }

    // ─────────────────────────────────────────────────────────────────
    // Veri satırı
    // [label1 LABEL_W] [value1 wt=1] [divider] [label2 LABEL_W] [value2 wt=1]
    // ─────────────────────────────────────────────────────────────────
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

    /** Konum etiketi (F1, F10, TEST…) */
    private fun labelText(text: String) = TextView(context).apply {
        this.text = text
        textSize = 10f
        setTextColor(COL_POS)
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(LABEL_W_DP), dp(ROW_H_DP))
    }

    /** Amper değeri (30_amp, empty, —…) */
    private fun valueText(value: String, weight: Float): TextView {
        val isEmpty = value == "—" || value == "empty"
        val color = when {
            value == "—" -> Color.parseColor("#445566")
            else         -> classTextColors[value] ?: Color.parseColor("#AAAACC")
        }
        return TextView(context).apply {
            text = value
            textSize = 12f
            typeface = if (isEmpty) Typeface.defaultFromStyle(Typeface.ITALIC)
            else Typeface.DEFAULT_BOLD
            setTextColor(color)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(ROW_H_DP), weight)
        }
    }

    /** Başlık metni */
    private fun headerText(text: String, weight: Float) = TextView(context).apply {
        this.text = text
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(COL_HEADER)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(HDR_H_DP), weight)
    }

    /** Yatay ince çizgi */
    private fun dividerLine() = View(context).apply {
        setBackgroundColor(COL_DIVIDER)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        )
    }

    /** Dikey ince çizgi */
    private fun verticalDivider(heightDp: Int) = View(context).apply {
        setBackgroundColor(COL_DIVIDER)
        layoutParams = LinearLayout.LayoutParams(dp(1), dp(heightDp))
    }

    /** Görünmez yer tutucu */
    private fun spacer(widthDp: Int, heightDp: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(heightDp))
    }

    // ─────────────────────────────────────────────────────────────────
    // Sol / sağ sütun ayırma
    // ─────────────────────────────────────────────────────────────────
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