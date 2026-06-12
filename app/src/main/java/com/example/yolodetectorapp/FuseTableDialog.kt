package com.example.yolodetectorapp

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import com.example.yolodetectorapp.db.AppDatabase
import com.example.yolodetectorapp.db.CombinationChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FuseTableDialog(
    context: Context,
    private val detections: List<Detection>,
    private val expectedClassIds: List<Int> = emptyList(),
    private val expectedCombinationId: String? = null,
    private val onRematchResult: ((matched: Boolean) -> Unit)? = null
) : Dialog(context) {

    private val classTextColors = mapOf(
        "2_amp"   to Color.parseColor("#AAAAAA"),
        "3_amp"   to Color.parseColor("#FF80AB"),
        "5_amp"   to Color.parseColor("#C4A882"),
        "7.5_amp" to Color.parseColor("#8B5E3C"),
        "10_amp"  to Color.parseColor("#FF3B30"),
        "15_amp"  to Color.parseColor("#2979FF"),
        "20_amp"  to Color.parseColor("#FFD600"),
        "25_amp"  to Color.parseColor("#E0E0E0"),
        "30_amp"  to Color.parseColor("#00C853"),
        "empty"   to Color.parseColor("#666680")
    )

    private val j3Labels = listOf("F10","F11","F12","F13","F14","F15","F16","F17","TEST")

    private val BG_DIALOG   = Color.parseColor("#E0101020")
    private val BG_HEADER   = Color.parseColor("#28FFFFFF")
    private val BG_ROW_ODD  = Color.parseColor("#14FFFFFF")
    private val BG_ROW_EVN  = Color.parseColor("#00000000")
    private val BG_EDITED   = Color.parseColor("#1A00E5A0")
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

    private val j2: List<Detection>
    private val j3: List<Detection>
    private val editedJ2: Array<String>
    private val editedJ3: Array<String>
    private val isEditedJ2 = BooleanArray(9) { false }
    private val isEditedJ3 = BooleanArray(9) { false }

    private val leftValueViews  = arrayOfNulls<TextView>(9)
    private val rightValueViews = arrayOfNulls<TextView>(9)
    private val leftCells       = arrayOfNulls<LinearLayout>(9)
    private val rightCells      = arrayOfNulls<LinearLayout>(9)

    private var rematchButton: Button? = null
    private var resultText: TextView? = null

    private val dialogScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        val (left, right) = splitColumns(detections)
        j2 = left
        j3 = right
        editedJ2 = Array(9) { i -> j2.getOrNull(i)?.className ?: "empty" }
        editedJ3 = Array(9) { i -> j3.getOrNull(i)?.className ?: "empty" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(BG_DIALOG)
                val r = dp(12).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        if (expectedCombinationId != null) {
            root.addView(TextView(context).apply {
                text = "Olması gereken dizilim türü: $expectedCombinationId"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isTablet) 13f else 10f)
                setTextColor(Color.parseColor("#AABBCC"))
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            root.addView(dividerLine())
        }

        root.addView(buildHeaderRow())
        root.addView(dividerLine())

        for (i in 0 until 9) {
            root.addView(buildDataRow(i, shaded = i % 2 == 0))
        }

        root.addView(dividerLine())

        val resultTv = TextView(context).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER
            textSize = if (isTablet) 34f else 26f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(if (isTablet) 56 else 44)
            )
        }
        resultText = resultTv
        root.addView(resultTv)

        val rematchBtn = Button(context).apply {
            text = "↺  Yeniden Eşleştir"
            visibility = View.GONE
            textSize = if (isTablet) 15f else 12f
            setTextColor(Color.parseColor("#00E5A0"))
            setBackgroundColor(Color.TRANSPARENT)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(if (isTablet) 52 else 40)
            )
            setOnClickListener { runRematch() }
        }
        rematchButton = rematchBtn
        root.addView(rematchBtn)

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

        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window?.attributes = window?.attributes?.also { a ->
            a.gravity = Gravity.BOTTOM
            a.y = 0
        }
    }

    override fun dismiss() {
        dialogScope.cancel()
        super.dismiss()
    }

    private fun buildHeaderRow(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(BG_HEADER)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, dp(HDR_H_DP), 1f)
            addView(spacer(LABEL_W_DP, HDR_H_DP))
            addView(headerText("Tespit", weight = 1f))
            addView(headerText("Gereken", weight = 1f))
        })
        addView(verticalDivider(HDR_H_DP))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, dp(HDR_H_DP), 1f)
            addView(spacer(LABEL_W_DP, HDR_H_DP))
            addView(headerText("Tespit", weight = 1f))
            addView(headerText("Gereken", weight = 1f))
        })
    }

    private fun buildDataRow(rowIndex: Int, shaded: Boolean): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (shaded) BG_ROW_ODD else BG_ROW_EVN)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val leftCell  = buildCellHalf(rowIndex, isLeft = true)
            val rightCell = buildCellHalf(rowIndex, isLeft = false)
            leftCells[rowIndex]  = leftCell
            rightCells[rowIndex] = rightCell
            addView(leftCell)
            addView(verticalDivider(ROW_H_DP))
            addView(rightCell)
        }

    private fun buildCellHalf(rowIndex: Int, isLeft: Boolean): LinearLayout {
        val hasOriginal = if (isLeft) j2.size > rowIndex else j3.size > rowIndex
        val className   = if (isLeft) editedJ2[rowIndex] else editedJ3[rowIndex]
        val displayText = if (hasOriginal) className else "—"
        val label       = if (isLeft) "F${rowIndex + 1}" else j3Labels[rowIndex]

        val expectedIndex = if (isLeft) rowIndex else rowIndex + 9
        val expectedClassId = expectedClassIds.getOrNull(expectedIndex)
        val expectedName = expectedClassId?.let { id ->
            YoloDetector.LABELS.getOrNull(id) ?: "—"
        } ?: "—"

        val tv = valueText(displayText, weight = 1f)
        if (isLeft) leftValueViews[rowIndex]  = tv
        else        rightValueViews[rowIndex] = tv

        val expectedTv = expectedValueText(expectedName, detectedName = displayText, weight = 1f)

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, dp(ROW_H_DP), 1f)
            isClickable = true
            isFocusable = true
            if (isMismatch(rowIndex, isLeft)) {
                setBackgroundColor(Color.parseColor("#33FF6B00"))
            }
            addView(labelText(label))
            addView(tv)
            addView(expectedTv)
            setOnClickListener { openPicker(rowIndex, isLeft) }
        }
    }

    private fun isMismatch(rowIndex: Int, isLeft: Boolean): Boolean {
        val expectedIndex = if (isLeft) rowIndex else rowIndex + 9
        val expectedClassId = expectedClassIds.getOrNull(expectedIndex) ?: return false
        val expectedName = YoloDetector.LABELS.getOrNull(expectedClassId) ?: return false
        val detectedName = if (isLeft) editedJ2[rowIndex] else editedJ3[rowIndex]
        val hasOriginal = if (isLeft) j2.size > rowIndex else j3.size > rowIndex
        return hasOriginal && detectedName != expectedName
    }

    private fun openPicker(rowIndex: Int, isLeft: Boolean) {
        val current = if (isLeft) editedJ2[rowIndex] else editedJ3[rowIndex]

        FusePickerDialog(context, current) { selected ->
            if (isLeft) {
                editedJ2[rowIndex]   = selected
                isEditedJ2[rowIndex] = true
                leftValueViews[rowIndex]?.let { refreshValue(it, selected) }
                leftCells[rowIndex]?.setBackgroundColor(BG_EDITED)
            } else {
                editedJ3[rowIndex]   = selected
                isEditedJ3[rowIndex] = true
                rightValueViews[rowIndex]?.let { refreshValue(it, selected) }
                rightCells[rowIndex]?.setBackgroundColor(BG_EDITED)
            }
            resultText?.visibility = View.GONE
            rematchButton?.visibility = View.VISIBLE
            rematchButton?.isEnabled = true
            rematchButton?.text = "↺  Yeniden Eşleştir"
        }.show()
    }

    private fun runRematch() {
        rematchButton?.isEnabled = false
        rematchButton?.text = "Eşleştiriliyor..."

        val editedDetections = buildEditedDetections()

        dialogScope.launch {
            try {
                val db = AppDatabase.getInstance(context)
                val validEntries = withContext(Dispatchers.IO) {
                    db.combinationDao().getAll()
                }
                val matchedId = CombinationChecker.check(editedDetections, validEntries, null)

                val matched = matchedId != null
                resultText?.let { tv ->
                    tv.visibility = View.VISIBLE
                    if (matched) {
                        tv.text = "OK"
                        tv.setTextColor(Color.parseColor("#00C853"))
                    } else {
                        tv.text = "NOK"
                        tv.setTextColor(Color.parseColor("#FF1744"))
                    }
                }
                onRematchResult?.invoke(matched)
                rematchButton?.visibility = View.GONE
            } catch (e: Exception) {
                android.util.Log.e("YOLO", "Yeniden eşleştirme hatası: ${e.message}", e)
                rematchButton?.isEnabled = true
                rematchButton?.text = "↺  Yeniden Eşleştir"
            }
        }
    }

    private fun buildEditedDetections(): List<Detection> {
        val result = mutableListOf<Detection>()
        for (i in 0 until 9) {
            val className = editedJ2[i]
            val classId   = YoloDetector.LABELS.indexOf(className).let { if (it < 0) 8 else it }
            val box       = j2.getOrNull(i)?.box ?: RectF(0.05f, i / 9f, 0.45f, (i + 1) / 9f)
            result.add(Detection(classId, className, 1f, box))
        }
        for (i in 0 until 9) {
            val className = editedJ3[i]
            val classId   = YoloDetector.LABELS.indexOf(className).let { if (it < 0) 8 else it }
            val box       = j3.getOrNull(i)?.box ?: RectF(0.55f, i / 9f, 0.95f, (i + 1) / 9f)
            result.add(Detection(classId, className, 1f, box))
        }
        return result
    }

    private fun refreshValue(tv: TextView, className: String) {
        tv.text = className
        tv.setTextColor(classTextColors[className] ?: Color.parseColor("#AAAACC"))
        tv.typeface = if (className == "empty") Typeface.defaultFromStyle(Typeface.ITALIC)
        else Typeface.DEFAULT_BOLD
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
        val color = when (value) {
            "—"  -> Color.parseColor("#445566")
            else -> classTextColors[value] ?: Color.parseColor("#AAAACC")
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

    private fun expectedValueText(expected: String, detectedName: String, weight: Float): TextView {
        val mismatch = expected != "—" && expected != detectedName
        val color = when {
            expected == "—" -> Color.parseColor("#445566")
            mismatch        -> classTextColors[expected] ?: Color.parseColor("#AAAACC")
            else            -> Color.parseColor("#445566")
        }
        return TextView(context).apply {
            text = if (expected == "—") "—" else expected
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_VALUE)
            typeface = if (mismatch) Typeface.DEFAULT_BOLD else Typeface.defaultFromStyle(Typeface.ITALIC)
            setTextColor(color)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(ROW_H_DP), weight)
        }
    }

    private fun headerText(text: String, weight: Float = 1f) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_HDR)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(COL_HEADER)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(HDR_H_DP), weight)
    }

    private fun dividerLine() = View(context).apply {
        setBackgroundColor(COL_DIVIDER)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
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
        val left   = sorted.filter { (it.box.left + it.box.right) / 2f < midX }
            .sortedBy { (it.box.top + it.box.bottom) / 2f }
        val right  = sorted.filter { (it.box.left + it.box.right) / 2f >= midX }
            .sortedBy { (it.box.top + it.box.bottom) / 2f }
        return Pair(left, right)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}