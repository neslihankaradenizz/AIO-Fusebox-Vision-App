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
import android.view.WindowManager
import android.widget.*

class FusePickerDialog(
    context: Context,
    private val currentValue: String,
    private val onSelected: (String) -> Unit
) : Dialog(context) {

    data class FuseOption(
        val className: String,
        val colorHex: String,
        val label: String,
        val drawableResId: Int   // 0 → görsel yok, renkli daire göster
    )

    private val fuseOptions = listOf(
        FuseOption("2_amp",   "#0DAD7A", "2A",    getDrawableId("ic_fuse_2amp")),
        FuseOption("3_amp",   "#3DAD3A", "3A",    getDrawableId("ic_fuse_3amp")),
        FuseOption("5_amp",   "#7B4FD6", "5A",    getDrawableId("ic_fuse_5amp")),
        FuseOption("7.5_amp", "#D46320", "7.5A",  getDrawableId("ic_fuse_7_5amp")),
        FuseOption("10_amp",  "#E8453C", "10A",   getDrawableId("ic_fuse_10amp")),
        FuseOption("15_amp",  "#E07B20", "15A",   getDrawableId("ic_fuse_15amp")),
        FuseOption("20_amp",  "#C9970A", "20A",   getDrawableId("ic_fuse_20amp")),
        FuseOption("25_amp",  "#0FA8CC", "25A",   getDrawableId("ic_fuse_25amp")),
        FuseOption("30_amp",  "#2E7DD6", "30A",   getDrawableId("ic_fuse_30amp")),
        FuseOption("empty",   "#666680", "Boş",   getDrawableId("ic_fuse_empty"))
    )

    private val isTablet  = context.resources.configuration.smallestScreenWidthDp >= 600
    private val IMG_SIZE  = if (isTablet) 56 else 44   // dp
    private val CARD_H    = if (isTablet) 96 else 76
    private val TEXT_SIZE = if (isTablet) 13f else 10f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2101028"))
                cornerRadius = dp(14).toFloat()
            }
        }

        // Başlık
        root.addView(TextView(context).apply {
            text = "Sigorta Türü Seç"
            setTextColor(Color.parseColor("#AABBDD"))
            textSize = if (isTablet) 15f else 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38)
            )
        })

        root.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            )
        })

        // Izgara (3 sütun)
        val grid = GridLayout(context).apply {
            columnCount = 3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(8) }
        }

        fuseOptions.forEach { option ->
            val isSelected = option.className == currentValue
            val card = buildCard(option, isSelected)
            card.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            card.setOnClickListener { onSelected(option.className); dismiss() }
            grid.addView(card)
        }

        root.addView(grid)

        // İptal
        root.addView(Button(context).apply {
            text = "İptal"
            setTextColor(Color.parseColor("#CC4444"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = if (isTablet) 14f else 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(if (isTablet) 44 else 36)
            )
            setOnClickListener { dismiss() }
        })

        setContentView(root)

        val w = if (isTablet) dp(420)
        else (context.resources.displayMetrics.widthPixels * 0.88).toInt()
        window?.setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
        window?.attributes = window?.attributes?.also { it.gravity = Gravity.CENTER }
    }

    private fun buildCard(option: FuseOption, isSelected: Boolean): LinearLayout {
        val color = Color.parseColor(option.colorHex)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(10), dp(6), dp(8))
            minimumHeight = dp(CARD_H)
            background = GradientDrawable().apply {
                setColor(
                    if (isSelected)
                        Color.argb(80, Color.red(color), Color.green(color), Color.blue(color))
                    else Color.parseColor("#1AFFFFFF")
                )
                cornerRadius = dp(8).toFloat()
                if (isSelected) setStroke(dp(2), color)
            }

            // Görsel alanı
            addView(buildFuseImage(option, color))

            // Etiket
            addView(TextView(context).apply {
                text = option.label
                setTextColor(color)
                textSize = TEXT_SIZE
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            })
        }
    }

    /**
     * Görsel varsa ImageView, yoksa renkli daire (fallback) gösterir.
     */
    private fun buildFuseImage(option: FuseOption, color: Int): View {
        val size = dp(IMG_SIZE)
        return if (option.drawableResId != 0) {
            ImageView(context).apply {
                setImageResource(option.drawableResId)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(2)
                }
            }
        } else {
            // Fallback: renkli daire (görsel henüz eklenmemişse)
            FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(2)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(45, Color.red(color), Color.green(color), Color.blue(color)))
                    setStroke(dp(2), color)
                }
                val dot = (size * 0.54).toInt()
                addView(View(context).apply {
                    layoutParams = FrameLayout.LayoutParams(dot, dot, Gravity.CENTER)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                    }
                })
            }
        }
    }

    /** Drawable resource ID'yi isme göre çözer; bulunamazsa 0 döner. */
    private fun getDrawableId(name: String): Int {
        return context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}