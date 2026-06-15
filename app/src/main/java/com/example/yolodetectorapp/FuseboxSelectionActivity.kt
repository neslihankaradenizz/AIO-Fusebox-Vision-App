package com.example.yolodetectorapp

import android.app.Activity
import android.content.Intent
import android.graphics.Matrix
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.yolodetectorapp.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FuseboxSelectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VEHICLE_NAME = "vehicle_name"
        const val RESULT_FUSEBOX_ID = "fusebox_id"
        const val RESULT_FUSEBOX_INDEX = "fusebox_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fusebox_selection)

        val vehicleName = intent.getStringExtra(EXTRA_VEHICLE_NAME) ?: ""

        val tvTitle    = findViewById<TextView>(R.id.tvTitle)
        val ivDiagram1 = findViewById<ImageView>(R.id.ivDiagram1)
        val ivDiagram2 = findViewById<ImageView>(R.id.ivDiagram2)
        val grid       = findViewById<GridLayout>(R.id.fuseboxGrid)

        tvTitle.text = "$vehicleName — Fusebox Seçin"

        val (img1, img2) = getVehicleImages(vehicleName)
        ivDiagram1.setImageResource(img1)
        ivDiagram2.setImageResource(img2)

        setupZoomableImage(ivDiagram1)
        setupZoomableImage(ivDiagram2)

        lifecycleScope.launch {
            val fuseboxIds = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext)
                    .combinationDao()
                    .getByVehicleName(vehicleName)
                    .map { it.combinationId }
            }

            grid.removeAllViews()
            fuseboxIds.forEachIndexed { index, combinationId ->
                val btn = Button(this@FuseboxSelectionActivity).apply {
                    text = combinationId
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                    backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.holo_red_dark)
                    val lp = GridLayout.LayoutParams().apply {
                        width = 0
                        height = GridLayout.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                        setMargins(8, 8, 8, 8)
                    }
                    layoutParams = lp
                    setOnClickListener {
                        val result = Intent().apply {
                            putExtra(RESULT_FUSEBOX_ID, combinationId)
                            putExtra(RESULT_FUSEBOX_INDEX, index + 1)
                        }
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    }
                }
                grid.addView(btn)
            }
        }
    }

    private fun setupZoomableImage(imageView: ImageView) {
        imageView.scaleType = ImageView.ScaleType.MATRIX
        imageView.post {
            val drawable = imageView.drawable ?: return@post
            val dw = drawable.intrinsicWidth.toFloat()
            val dh = drawable.intrinsicHeight.toFloat()
            val vw = imageView.width.toFloat()
            val vh = imageView.height.toFloat()
            val scale = minOf(vw / dw, vh / dh)
            val initMatrix = Matrix()
            initMatrix.setScale(scale, scale)
            initMatrix.postTranslate((vw - dw * scale) / 2f, (vh - dh * scale) / 2f)
            imageView.imageMatrix = initMatrix
        }

        var lastX = 0f
        var lastY = 0f
        var isDragging = false

        val scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val m = Matrix(imageView.imageMatrix)
                    m.postScale(detector.scaleFactor, detector.scaleFactor,
                        detector.focusX, detector.focusY)
                    imageView.imageMatrix = m
                    return true
                }
            })

        imageView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y; isDragging = true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging && !scaleDetector.isInProgress) {
                        val m = Matrix(imageView.imageMatrix)
                        m.postTranslate(event.x - lastX, event.y - lastY)
                        imageView.imageMatrix = m
                        lastX = event.x; lastY = event.y
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
            }
            imageView.performClick()
            true
        }
    }

    private fun getVehicleImages(vehicleName: String): Pair<Int, Int> {
        return when (vehicleName) {
            "Novociti Life" -> Pair(
                getDrawableId("novociti_life_1") ?: R.drawable.placeholder_fusebox,
                getDrawableId("novociti_life_2") ?: R.drawable.placeholder_fusebox
            )
            "Novociti" -> Pair(
                getDrawableId("novociti_1") ?: R.drawable.placeholder_fusebox,
                getDrawableId("novociti_2") ?: R.drawable.placeholder_fusebox
            )
            else -> Pair(R.drawable.placeholder_fusebox, R.drawable.placeholder_fusebox)
        }
    }

    private fun getDrawableId(name: String): Int? {
        val id = resources.getIdentifier(name, "drawable", packageName)
        return if (id != 0) id else null
    }
}