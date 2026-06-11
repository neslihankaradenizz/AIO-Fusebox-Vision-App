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
        if (bmp == null || bmp.isRecycled) {
            finish()
            return
        }

        val imageView  = findViewById<ImageView>(R.id.resultImageView)
        val boxOverlay = findViewById<BoxOverlayView>(R.id.resultBoxOverlay)
        val tvResult   = findViewById<TextView>(R.id.tvResult)
        val tvDebug    = findViewById<TextView>(R.id.tvDebug)
        val btnMatch   = findViewById<Button>(R.id.btnMatch)
        val btnTable   = findViewById<Button>(R.id.btnTable)
        val btnRetake  = findViewById<Button>(R.id.btnRetake)

        imageView.setImageBitmap(bmp)

        tvDebug.text = "🔍 Analiz ediliyor..."
        tvDebug.setTextColor("#FFFFFF".toColorInt())
        btnMatch.isEnabled = false

        lifecycleScope.launch {
            val detections = withContext(Dispatchers.Default) {
                YoloDetector(this@ResultActivity).detect(bmp)
            }

            pendingDetections = detections

            imageView.post {
                boxOverlay.setDetections(
                    detections,
                    bmp.width,
                    bmp.height,
                    imageView.width,
                    imageView.height,
                    useFitCenter = true
                )
            }

            if (detections.isEmpty()) {
                tvDebug.text = "⚠️ Hiç tespit yok!"
                tvDebug.setTextColor("#FF1744".toColorInt())
            } else {
                val summary = detections
                    .groupBy { it.className }
                    .map { (cls, list) -> "${cls}×${list.size}" }
                    .joinToString("  ")
                tvDebug.text = "✅ ${detections.size} tespit: $summary"
                tvDebug.setTextColor("#00C853".toColorInt())
            }

            btnMatch.isEnabled = true
        }

        // Görüntü Eşleştir
        btnMatch.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val db = AppDatabase.getInstance(applicationContext)
                    val validEntries = withContext(Dispatchers.IO) {
                        db.combinationDao().getAll()
                    }

                    // ← combinationId = null: tüm kombinasyonları sırayla kontrol eder
                    val matchedId = CombinationChecker.check(
                        pendingDetections,
                        validEntries,
                        combinationId = null
                    )

                    tvResult.visibility = View.VISIBLE
                    tvResult.bringToFront()

                    if (matchedId != null) {
                        // OK — hangi kombinasyona ait olduğunu loga yaz
                        tvResult.text = "OK"
                        tvResult.setTextColor("#00C853".toColorInt())
                        android.util.Log.d("YOLO", "Eşleşen kombinasyon: $matchedId")
                    } else {
                        // NOK — hiçbir kombinasyon tutmadı
                        tvResult.text = "NOK"
                        tvResult.setTextColor("#FF1744".toColorInt())
                    }

                    btnMatch.visibility = View.GONE
                    btnTable.visibility = View.VISIBLE
                    btnRetake.visibility = View.VISIBLE
                    btnRetake.setOnClickListener { finish() }

                } catch (e: Exception) {
                    android.util.Log.e("YOLO", "HATA: ${e.message}", e)
                }
            }
        }

        // Tespit Tablosu
        btnTable.setOnClickListener {
            FuseTableDialog(this, pendingDetections).show()
        }
    }
}