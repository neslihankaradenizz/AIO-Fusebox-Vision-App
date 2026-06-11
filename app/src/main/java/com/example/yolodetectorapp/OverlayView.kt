package com.example.yolodetectorapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val overlayPaint = Paint().apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val cornerPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    private var roiRect: RectF? = null
    private val cornerLength = 60f  // L uzunlugu

    fun setRoiRect(rect: RectF) {
        roiRect = rect
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val roi = roiRect ?: return

        val w = width.toFloat()
        val h = height.toFloat()

        //roi disini karart
        canvas.drawRect(0f, 0f, w, roi.top, overlayPaint)
        canvas.drawRect(0f, roi.bottom, w, h, overlayPaint)
        canvas.drawRect(0f, roi.top, roi.left, roi.bottom, overlayPaint)
        canvas.drawRect(roi.right, roi.top, w, roi.bottom, overlayPaint)

        // Sol ust L
        canvas.drawLine(roi.left, roi.top + cornerLength, roi.left, roi.top, cornerPaint)
        canvas.drawLine(roi.left, roi.top, roi.left + cornerLength, roi.top, cornerPaint)

        // Sag ust L
        canvas.drawLine(roi.right - cornerLength, roi.top, roi.right, roi.top, cornerPaint)
        canvas.drawLine(roi.right, roi.top, roi.right, roi.top + cornerLength, cornerPaint)

        // Sol alt  L
        canvas.drawLine(roi.left, roi.bottom - cornerLength, roi.left, roi.bottom, cornerPaint)
        canvas.drawLine(roi.left, roi.bottom, roi.left + cornerLength, roi.bottom, cornerPaint)

        // Sag alt  L
        canvas.drawLine(roi.right - cornerLength, roi.bottom, roi.right, roi.bottom, cornerPaint)
        canvas.drawLine(roi.right, roi.bottom, roi.right, roi.bottom - cornerLength, cornerPaint)
    }
}