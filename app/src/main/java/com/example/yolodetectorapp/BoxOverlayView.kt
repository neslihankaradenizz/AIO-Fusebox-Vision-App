package com.example.yolodetectorapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class BoxOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val classColors = mapOf(
        "10_amp"  to Color.parseColor("#F44336"),
        "15_amp"  to Color.parseColor("#2196F3"),
        "20_amp"  to Color.parseColor("#FFEB3B"),
        "25_amp"  to Color.parseColor("#FFFFFF"),
        "2_amp"   to Color.parseColor("#9C27B0"),
        "30_amp"  to Color.parseColor("#4CAF50"),
        "3_amp"   to Color.parseColor("#3DAD3A"),   // FIX 4: eksik renk eklendi
        "5_amp"   to Color.parseColor("#A0785A"),
        "7.5_amp" to Color.parseColor("#795548"),
        "empty"   to Color.parseColor("#9E9E9E")
    )

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    // FIX 3: döngü dışına alındı, her onDraw'da yeni nesne oluşturulmuyor

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f
        isFakeBoldText = true
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private var detections: List<Detection> = emptyList()
    private var srcW = 0
    private var srcH = 0
    private var pvW  = 0
    private var pvH  = 0
    private var fitCenter = false

    fun setDetections(list: List<Detection>,
                      bitmapW: Int, bitmapH: Int,
                      previewW: Int, previewH: Int,
                      useFitCenter: Boolean = false) {
        detections = list
        srcW = bitmapW; srcH = bitmapH
        pvW  = previewW; pvH  = previewH
        fitCenter = useFitCenter
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty() || srcW <= 0 || srcH <= 0 || pvW <= 0 || pvH <= 0) return

        val scale = if (fitCenter)
            minOf(width.toFloat() / srcW, height.toFloat() / srcH)
        else
            maxOf(width.toFloat() / srcW, height.toFloat() / srcH)

        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val offsetX = (width  - scaledW) / 2f
        val offsetY = (height - scaledH) / 2f

        for (det in detections) {
            val color = classColors[det.className] ?: Color.WHITE

            val left   = offsetX + det.box.left   * scaledW
            val top    = offsetY + det.box.top     * scaledH
            val right  = offsetX + det.box.right   * scaledW
            val bottom = offsetY + det.box.bottom  * scaledH

            if (right < 0 || left > pvW || bottom < 0 || top > pvH) continue

            val rect = RectF(left, top, right, bottom)

            boxPaint.color = color
            canvas.drawRoundRect(rect, 8f, 8f, boxPaint)

            val cl = 14f
            cornerPaint.color = color
            canvas.drawLine(left, top + cl, left, top, cornerPaint)
            canvas.drawLine(left, top, left + cl, top, cornerPaint)
            canvas.drawLine(right - cl, top, right, top, cornerPaint)
            canvas.drawLine(right, top, right, top + cl, cornerPaint)
            canvas.drawLine(left, bottom - cl, left, bottom, cornerPaint)
            canvas.drawLine(left, bottom, left + cl, bottom, cornerPaint)
            canvas.drawLine(right - cl, bottom, right, bottom, cornerPaint)
            canvas.drawLine(right, bottom, right, bottom - cl, cornerPaint)

            val label   = det.className
            val padding = 2f
            val textW   = textPaint.measureText(label)
            val textH   = textPaint.descent() - textPaint.ascent()
            val labelH  = textH + padding * 2
            val labelTop  = if (top - labelH >= 0f) top - labelH else top
            val labelLeft = minOf(left, pvW - textW - padding * 2)
            val labelRect = RectF(labelLeft, labelTop,
                labelLeft + textW + padding * 2, labelTop + labelH)

            labelBgPaint.color = color
            canvas.drawRoundRect(labelRect, 4f, 4f, labelBgPaint)
            canvas.drawText(label,
                labelRect.left + padding,
                labelRect.top + padding - textPaint.ascent(),
                textPaint)
        }
    }
}