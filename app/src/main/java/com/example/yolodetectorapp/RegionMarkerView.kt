package com.example.yolodetectorapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class RegionMarkerView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    data class Region(
        val label: String,
        val origX: Float,
        val origY: Float,
        val fuseboxIds: List<String>,
        val firstIndex: Int = 1
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
        alpha = 220
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 38f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private var regions: List<Region> = emptyList()
    private var imgMatrix: Matrix = Matrix()

    init {
        isClickable = false
        isFocusable = false
    }

    fun setData(regions: List<Region>, matrix: Matrix) {
        this.regions = regions
        this.imgMatrix = Matrix(matrix)
        invalidate()
    }

    fun updateMatrix(matrix: Matrix) {
        this.imgMatrix = Matrix(matrix)
        invalidate()
    }

    fun regionAt(viewX: Float, viewY: Float): Region? {
        return regions.firstOrNull { r ->
            val pts = floatArrayOf(r.origX, r.origY)
            imgMatrix.mapPoints(pts)
            val dx = viewX - pts[0]
            val dy = viewY - pts[1]
            dx * dx + dy * dy <= 65f * 65f
        }
    }

    override fun onDraw(canvas: Canvas) {
        regions.forEach { region ->
            val pts = floatArrayOf(region.origX, region.origY)
            imgMatrix.mapPoints(pts)
            canvas.drawCircle(pts[0], pts[1], 32f, fillPaint)
            canvas.drawText(region.label, pts[0], pts[1] + textPaint.textSize / 3f, textPaint)
        }
    }
}