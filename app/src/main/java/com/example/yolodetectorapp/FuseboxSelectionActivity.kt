package com.example.yolodetectorapp

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Matrix
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.yolodetectorapp.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class FuseboxSelectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VEHICLE_NAME = "vehicle_name"
        const val RESULT_FUSEBOX_ID = "fusebox_id"
        const val RESULT_FUSEBOX_INDEX = "fusebox_index"
    }

    private lateinit var tvFuseboxLabel: TextView
    private lateinit var fuseboxButtonPanel: LinearLayout
    private lateinit var overlay1: RegionMarkerView
    private lateinit var overlay2: RegionMarkerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fusebox_selection)

        val vehicleName = intent.getStringExtra(EXTRA_VEHICLE_NAME) ?: ""

        val tvTitle    = findViewById<TextView>(R.id.tvTitle)
        val ivDiagram1 = findViewById<ImageView>(R.id.ivDiagram1)
        val ivDiagram2 = findViewById<ImageView>(R.id.ivDiagram2)
        overlay1       = findViewById(R.id.overlayDiagram1)
        overlay2       = findViewById(R.id.overlayDiagram2)
        tvFuseboxLabel = findViewById(R.id.tvFuseboxLabel)
        fuseboxButtonPanel = findViewById(R.id.fuseboxButtonPanel)

        tvTitle.text = "$vehicleName — Fusebox Seçin"

        val (img1, img2) = getVehicleImages(vehicleName)
        ivDiagram1.setImageResource(img1)
        ivDiagram2.setImageResource(img2)

        lifecycleScope.launch {
            val allIds = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext)
                    .combinationDao()
                    .getByVehicleName(vehicleName)
                    .map { it.combinationId }
                    .sorted()
            }

            val (regions1, regions2) = buildRegions(vehicleName, allIds)
            setupZoomableImage(ivDiagram1, overlay1, regions1)
            setupZoomableImage(ivDiagram2, overlay2, regions2)
        }
    }

    private fun buildRegions(
        vehicleName: String,
        sortedIds: List<String>
    ): Pair<List<RegionMarkerView.Region>, List<RegionMarkerView.Region>> {
        return when (vehicleName) {
            "Novociti Life" -> {
                val r1 = listOf(
                    RegionMarkerView.Region("1", 0.822f, 0.230f,
                        listOfNotNull(sortedIds.getOrNull(0), sortedIds.getOrNull(1), sortedIds.getOrNull(2)),
                        firstIndex = 1),
                    RegionMarkerView.Region("2", 0.460f, 0.265f,
                        listOfNotNull(sortedIds.getOrNull(3), sortedIds.getOrNull(4)),
                        firstIndex = 4)
                )
                val r2 = listOf(
                    RegionMarkerView.Region("3", 0.423f, 0.393f,
                        listOfNotNull(sortedIds.getOrNull(5), sortedIds.getOrNull(6)),
                        firstIndex = 6)
                )
                Pair(r1, r2)
            }
            else -> Pair(emptyList(), emptyList())
        }
    }

    private fun setupZoomableImage(
        imageView: ImageView,
        overlayView: RegionMarkerView,
        regions: List<RegionMarkerView.Region>
    ) {
        imageView.scaleType = ImageView.ScaleType.MATRIX

        imageView.post {
            val drawable = imageView.drawable ?: return@post
            val dw = drawable.intrinsicWidth.toFloat()
            val dh = drawable.intrinsicHeight.toFloat()
            val vw = imageView.width.toFloat()
            val vh = imageView.height.toFloat()
            if (dw == 0f || dh == 0f) return@post
            val scale = minOf(vw / dw, vh / dh)
            val initMatrix = Matrix()
            initMatrix.setScale(scale, scale)
            initMatrix.postTranslate((vw - dw * scale) / 2f, (vh - dh * scale) / 2f)
            imageView.imageMatrix = initMatrix

            // normalize (0-1) → orijinal piksel
            val pixelRegions = regions.map { r ->
                r.copy(origX = r.origX * dw, origY = r.origY * dh)
            }
            overlayView.setData(pixelRegions, initMatrix)
        }

        var lastX = 0f
        var lastY = 0f
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false

        val scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val m = Matrix(imageView.imageMatrix)
                    m.postScale(detector.scaleFactor, detector.scaleFactor,
                        detector.focusX, detector.focusY)
                    imageView.imageMatrix = m
                    overlayView.updateMatrix(m)
                    return true
                }
            })

        imageView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y
                    touchStartX = event.x; touchStartY = event.y
                    isDragging = true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging && !scaleDetector.isInProgress) {
                        val m = Matrix(imageView.imageMatrix)
                        m.postTranslate(event.x - lastX, event.y - lastY)
                        imageView.imageMatrix = m
                        overlayView.updateMatrix(m)
                        lastX = event.x; lastY = event.y
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - touchStartX
                    val dy = event.y - touchStartY
                    val moved = sqrt((dx * dx + dy * dy).toDouble())
                    if (moved < 20 && !scaleDetector.isInProgress) {
                        val hit = overlayView.regionAt(event.x, event.y)
                        if (hit != null) {
                            zoomToRegion(imageView, overlayView, hit.origX, hit.origY)
                            showFuseboxButtons(hit)
                        }
                    }
                    isDragging = false
                }
                MotionEvent.ACTION_CANCEL -> isDragging = false
            }
            imageView.performClick()
            true
        }
    }

    private fun zoomToRegion(
        imageView: ImageView,
        overlayView: RegionMarkerView,
        origX: Float,
        origY: Float
    ) {
        val drawable = imageView.drawable ?: return
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()
        val vw = imageView.width.toFloat()
        val vh = imageView.height.toFloat()
        val fitScale = minOf(vw / dw, vh / dh)
        val zoomScale = fitScale * 4f

        val targetMatrix = Matrix()
        targetMatrix.setScale(zoomScale, zoomScale)
        targetMatrix.postTranslate(
            vw / 2f - origX * zoomScale,
            vh / 2f - origY * zoomScale
        )

        val startValues = FloatArray(9)
        val endValues = FloatArray(9)
        Matrix(imageView.imageMatrix).getValues(startValues)
        targetMatrix.getValues(endValues)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                val interp = FloatArray(9) { i -> startValues[i] + (endValues[i] - startValues[i]) * t }
                val m = Matrix()
                m.setValues(interp)
                imageView.imageMatrix = m
                overlayView.updateMatrix(m)
            }
            start()
        }
    }

    private fun showFuseboxButtons(region: RegionMarkerView.Region) {
        tvFuseboxLabel.text = "Bölge ${region.label} — Fusebox seçin:"
        fuseboxButtonPanel.visibility = android.view.View.VISIBLE
        fuseboxButtonPanel.removeAllViews()

        // Geri butonu
        val backBtn = Button(this).apply {
            text = "← Geri"
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.darker_gray)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(8, 0, 16, 0)
            layoutParams = lp
            setOnClickListener { resetSelection() }
        }
        fuseboxButtonPanel.addView(backBtn)

        // Fusebox numarası butonları
        region.fuseboxIds.forEachIndexed { index, combinationId ->
            val btn = Button(this).apply {
                text = "${region.firstIndex + index}"
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.holo_red_dark)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(8, 0, 8, 0)
                layoutParams = lp
                setOnClickListener {
                    val result = Intent().apply {
                        putExtra(RESULT_FUSEBOX_ID, combinationId)
                        putExtra(RESULT_FUSEBOX_INDEX, region.firstIndex + index)
                    }
                    setResult(Activity.RESULT_OK, result)
                    finish()
                }
            }
            fuseboxButtonPanel.addView(btn)
        }
    }

    private fun resetSelection() {
        // Paneli kapat
        fuseboxButtonPanel.visibility = android.view.View.GONE
        fuseboxButtonPanel.removeAllViews()
        tvFuseboxLabel.text = "Görselde bir bölgeye (kırmızı daire) dokunun"

        // Her iki görseli fit-to-view'e sıfırla
        listOf(
            Pair(findViewById<ImageView>(R.id.ivDiagram1), overlay1),
            Pair(findViewById<ImageView>(R.id.ivDiagram2), overlay2)
        ).forEach { (iv, ov) ->
            val drawable = iv.drawable ?: return@forEach
            val dw = drawable.intrinsicWidth.toFloat()
            val dh = drawable.intrinsicHeight.toFloat()
            val vw = iv.width.toFloat()
            val vh = iv.height.toFloat()
            if (dw == 0f || dh == 0f) return@forEach
            val scale = minOf(vw / dw, vh / dh)
            val m = Matrix()
            m.setScale(scale, scale)
            m.postTranslate((vw - dw * scale) / 2f, (vh - dh * scale) / 2f)
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 300
                val start = FloatArray(9); val end = FloatArray(9)
                Matrix(iv.imageMatrix).getValues(start); m.getValues(end)
                addUpdateListener { anim ->
                    val t = anim.animatedFraction
                    val interp = FloatArray(9) { i -> start[i] + (end[i] - start[i]) * t }
                    val rm = Matrix(); rm.setValues(interp)
                    iv.imageMatrix = rm; ov.updateMatrix(rm)
                }
                start()
            }
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