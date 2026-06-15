package com.example.yolodetectorapp

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.yolodetectorapp.db.AppDatabase
import com.example.yolodetectorapp.db.CombinationChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.toColorInt

class ResultActivity : AppCompatActivity() {
    companion object {
        var pendingBitmap: Bitmap? = null
        var pendingDetections: List<Detection> = emptyList()
    }

    private var lastExpectedCombinationId: String? = null
    private var lastExpectedClassIds: List<Int> = emptyList()

    override fun onDestroy() {
        super.onDestroy()
        pendingBitmap?.recycle()
        pendingBitmap = null
        pendingDetections = emptyList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val bmp = pendingBitmap
        if (bmp == null || bmp.isRecycled) { finish(); return }

        val selectedVehicle = intent.getStringExtra(MainActivity.EXTRA_VEHICLE_NAME) ?: ""

        val imageView  = findViewById<ImageView>(R.id.resultImageView)
        val boxOverlay = findViewById<BoxOverlayView>(R.id.resultBoxOverlay)
        val tvResult   = findViewById<TextView>(R.id.tvResult)
        val tvDebug    = findViewById<TextView>(R.id.tvDebug)
        val tvVehicle  = findViewById<TextView>(R.id.tvVehicleName)
        val btnMatch   = findViewById<Button>(R.id.btnMatch)
        val btnTable   = findViewById<Button>(R.id.btnTable)
        val btnRetake  = findViewById<Button>(R.id.btnRetake)

        tvVehicle.text = selectedVehicle

        imageView.setImageBitmap(bmp)
        tvDebug.text = "🔍 Analiz ediliyor..."
        tvDebug.setTextColor("#FFFFFF".toColorInt())
        btnMatch.isEnabled = false

        btnRetake.setOnClickListener { finish() }

        lifecycleScope.launch {
            val detections = withContext(Dispatchers.Default) {
                val detector = YoloDetector(this@ResultActivity)
                detector.detect(bmp)
            }

            pendingDetections = detections

            imageView.post {
                boxOverlay.setDetections(
                    detections, bmp.width, bmp.height,
                    imageView.width, imageView.height, useFitCenter = true
                )
            }

            if (detections.isEmpty()) {
                tvDebug.text = "Tespit edilen sigorta sayısı: 0"
                tvDebug.setTextColor("#CCCCCC".toColorInt())
            } else {
                tvDebug.text = "Tespit edilen sigorta sayısı: ${detections.size}"
                tvDebug.setTextColor("#CCCCCC".toColorInt())
            }

            btnMatch.isEnabled = true
        }

        btnMatch.setOnClickListener {
            btnMatch.isEnabled = false

            lifecycleScope.launch {
                try {
                    val db = AppDatabase.getInstance(applicationContext)
                    val validEntries = withContext(Dispatchers.IO) {
                        if (selectedVehicle.isNotEmpty())
                            db.combinationDao().getByVehicleName(selectedVehicle)
                        else
                            db.combinationDao().getAll()
                    }

                    val result = CombinationChecker.checkWithExpected(
                        pendingDetections, validEntries, combinationId = null
                    )

                    val matchedId = result.matchedId
                    lastExpectedClassIds = result.expectedClassIds
                    lastExpectedCombinationId = result.expectedCombinationId

                    tvResult.visibility = View.VISIBLE
                    tvResult.bringToFront()

                    if (matchedId != null) {
                        tvResult.text = "OK"
                        tvResult.setTextColor("#00C853".toColorInt())
                    } else {
                        tvResult.text = "NOK"
                        tvResult.setTextColor("#FF1744".toColorInt())
                    }

                    btnMatch.visibility = View.GONE
                    btnTable.visibility = View.VISIBLE
                    btnRetake.visibility = View.VISIBLE

                } catch (e: Exception) {
                    android.util.Log.e("YOLO", "Eşleştirme hatası: ${e.message}", e)
                    btnMatch.isEnabled = true
                }
            }
        }

        btnTable.setOnClickListener {
            FuseTableDialog(this,
                pendingDetections,
                lastExpectedClassIds,
                lastExpectedCombinationId,
                selectedVehicle ) { matched ->

                if (matched) {
                    tvResult.text = "OK"
                    tvResult.setTextColor("#00C853".toColorInt())
                } else {
                    tvResult.text = "NOK"
                    tvResult.setTextColor("#FF1744".toColorInt())
                }
            }.show()
        }
    }
}