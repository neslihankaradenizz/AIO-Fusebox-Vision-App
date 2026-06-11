package com.example.yolodetectorapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var roiView: android.view.View
    private lateinit var btnCapture: Button
    private lateinit var btnFlash: ImageButton
    private lateinit var imageCapture: ImageCapture

    private var flashEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        roiView     = findViewById(R.id.roiView)
        btnCapture  = findViewById(R.id.btnCapture)
        btnFlash    = findViewById(R.id.btnFlash)

        btnFlash.setOnClickListener {
            flashEnabled = !flashEnabled
            btnFlash.setImageResource(
                if (flashEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            )
            if (::imageCapture.isInitialized) {
                imageCapture.flashMode = if (flashEnabled)
                    ImageCapture.FLASH_MODE_ON
                else
                    ImageCapture.FLASH_MODE_OFF
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), 100
            )
        }

        btnCapture.setOnClickListener {
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bmp = image.toBitmap().let { raw ->
                            val matrix = Matrix()
                            matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                            val rotated = Bitmap.createBitmap(
                                raw, 0, 0, raw.width, raw.height, matrix, true
                            )
                            raw.recycle()
                            rotated
                        }
                        image.close()

                        val roi = calcNormalizedRoi()
                        val croppedBmp = cropToRoi(bmp, roi)
                        bmp.recycle()

                        ResultActivity.pendingBitmap = croppedBmp
                        startActivity(Intent(this@MainActivity, ResultActivity::class.java))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(
                            this@MainActivity,
                            "Hata: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }

    private fun calcNormalizedRoi(): RectF {
        val roiLoc     = IntArray(2)
        val previewLoc = IntArray(2)
        previewView.getLocationOnScreen(previewLoc)
        roiView.getLocationOnScreen(roiLoc)

        val left   = (roiLoc[0] - previewLoc[0]).toFloat() / previewView.width
        val top    = (roiLoc[1] - previewLoc[1]).toFloat() / previewView.height
        val right  = left + roiView.width.toFloat() / previewView.width
        val bottom = top  + roiView.height.toFloat() / previewView.height

        return RectF(
            left.coerceIn(0f, 1f),
            top.coerceIn(0f, 1f),
            right.coerceIn(0f, 1f),
            bottom.coerceIn(0f, 1f)
        )
    }

    private fun cropToRoi(bmp: Bitmap, roi: RectF): Bitmap {
        val x = (roi.left   * bmp.width).toInt()
        val y = (roi.top    * bmp.height).toInt()
        val w = ((roi.right  - roi.left) * bmp.width).toInt()
        val h = ((roi.bottom - roi.top)  * bmp.height).toInt()
        return Bitmap.createBitmap(bmp, x, y, w, h)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val displayMetrics = resources.displayMetrics
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(displayMetrics.widthPixels, displayMetrics.heightPixels),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setResolutionSelector(resolutionSelector)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )

            roiView.post {
                val roiLoc     = IntArray(2)
                val overlayLoc = IntArray(2)
                roiView.getLocationOnScreen(roiLoc)
                overlayView.getLocationOnScreen(overlayLoc)

                overlayView.setRoiRect(RectF(
                    (roiLoc[0] - overlayLoc[0]).toFloat(),
                    (roiLoc[1] - overlayLoc[1]).toFloat(),
                    (roiLoc[0] - overlayLoc[0] + roiView.width).toFloat(),
                    (roiLoc[1] - overlayLoc[1] + roiView.height).toFloat()
                ))
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Kamera izni gerekli!", Toast.LENGTH_SHORT).show()
        }
    }
}