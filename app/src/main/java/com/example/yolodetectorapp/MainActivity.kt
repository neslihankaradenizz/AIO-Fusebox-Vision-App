package com.example.yolodetectorapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.view.ScaleGestureDetector
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var roiView: android.view.View
    private lateinit var btnCapture: Button
    private lateinit var btnFlash: ImageButton
    private lateinit var imageCapture: ImageCapture
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var tvOrientationWarning: TextView
    private lateinit var orientationListener: android.view.OrientationEventListener
    private lateinit var vehicleSpinner: Spinner
    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var btnBrightnessToggle: TextView
    private lateinit var tvSelectedFusebox: TextView

    private var camera: Camera? = null
    private var flashEnabled = false
    private var brightnessVisible = false
    private val captureExecutor = Executors.newSingleThreadExecutor()

    private var selectedFuseboxId: String? = null
    private var spinnerReady = false
    private var selectedVehiclePosition = 0
    private var shouldResetOnResume = false

    companion object {
        val VEHICLE_LIST = listOf("Araç Seçiniz", "Novociti Life", "Novociti")
        const val EXTRA_VEHICLE_NAME = "vehicle_name"
        const val EXTRA_FUSEBOX_ID = "fusebox_id"
    }

    private val fuseboxSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedFuseboxId = result.data?.getStringExtra(FuseboxSelectionActivity.RESULT_FUSEBOX_ID)
            tvSelectedFusebox.text = "Seçilen Fusebox: $selectedFuseboxId"
            tvSelectedFusebox.visibility = View.VISIBLE
        }
        // Araç adını geri yükle (OK veya iptal fark etmez)
        spinnerReady = false
        vehicleSpinner.setSelection(selectedVehiclePosition, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView          = findViewById(R.id.previewView)
        overlayView          = findViewById(R.id.overlayView)
        roiView              = findViewById(R.id.roiView)
        btnCapture           = findViewById(R.id.btnCapture)
        btnFlash             = findViewById(R.id.btnFlash)
        zoomSeekBar          = findViewById(R.id.zoomSeekBar)
        tvOrientationWarning = findViewById(R.id.tvOrientationWarning)
        vehicleSpinner       = findViewById(R.id.vehicleSpinner)
        brightnessSeekBar    = findViewById(R.id.brightnessSeekBar)
        btnBrightnessToggle  = findViewById(R.id.btnBrightnessToggle)
        tvSelectedFusebox    = findViewById(R.id.tvSelectedFusebox)

        brightnessSeekBar.progress = 50
        applyBrightness(50)

        btnBrightnessToggle.setOnClickListener {
            brightnessVisible = !brightnessVisible
            brightnessSeekBar.visibility = if (brightnessVisible) View.VISIBLE else View.GONE
            btnBrightnessToggle.alpha = if (brightnessVisible) 1f else 0.5f
        }

        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) applyBrightness(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val spinnerAdapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, VEHICLE_LIST
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).setTextColor(Color.WHITE)
                view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).setTextColor(Color.WHITE)
                view.setBackgroundColor(Color.parseColor("#222222"))
                view.setPadding(24, 20, 24, 20)
                return view
            }
        }
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vehicleSpinner.adapter = spinnerAdapter

        vehicleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (!spinnerReady) { spinnerReady = true; return }
                if (position == 0) {
                    selectedFuseboxId = null
                    tvSelectedFusebox.visibility = View.GONE
                    selectedVehiclePosition = 0
                    return
                }
                selectedVehiclePosition = position
                val selectedVehicle = VEHICLE_LIST[position]
                val intent = Intent(this@MainActivity, FuseboxSelectionActivity::class.java)
                intent.putExtra(FuseboxSelectionActivity.EXTRA_VEHICLE_NAME, selectedVehicle)
                fuseboxSelectionLauncher.launch(intent)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        vehicleSpinner.setOnTouchListener { _, _ ->
            spinnerReady = false
            vehicleSpinner.setSelection(0, false)
            false
        }
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val zoomState = camera?.cameraInfo?.zoomState?.value ?: return
                val zoom = zoomState.minZoomRatio +
                        (zoomState.maxZoomRatio - zoomState.minZoomRatio) * (progress / 100f)
                camera?.cameraControl?.setZoomRatio(zoom)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        btnFlash.setOnClickListener {
            flashEnabled = !flashEnabled
            btnFlash.setImageResource(
                if (flashEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            )
            if (::imageCapture.isInitialized) {
                imageCapture.flashMode = if (flashEnabled)
                    ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            }
        }

        orientationListener = object : android.view.OrientationEventListener(this@MainActivity) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val isLandscape = orientation in 45..135 || orientation in 225..315
                tvOrientationWarning.visibility =
                    if (isLandscape) View.VISIBLE else View.GONE
                btnCapture.isEnabled = !isLandscape
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }

        btnCapture.setOnClickListener {
            if (vehicleSpinner.selectedItemPosition == 0) {
                Toast.makeText(this, "Lütfen önce araç tipini seçiniz", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedFuseboxId == null) {
                Toast.makeText(this, "Lütfen önce fusebox seçiniz", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnCapture.isEnabled = false
            val selectedVehicle = VEHICLE_LIST[vehicleSpinner.selectedItemPosition]
            val roi = calcNormalizedRoi()
            imageCapture.takePicture(
                captureExecutor,
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

                        val croppedBmp = cropToRoi(bmp, roi)
                        bmp.recycle()

                        ResultActivity.pendingBitmap = croppedBmp
                        val intent = Intent(this@MainActivity, ResultActivity::class.java)
                        intent.putExtra(EXTRA_VEHICLE_NAME, selectedVehicle)
                        intent.putExtra(EXTRA_FUSEBOX_ID, selectedFuseboxId)
                        runOnUiThread {
                            shouldResetOnResume = true
                            startActivity(intent)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        runOnUiThread {
                            btnCapture.isEnabled = true
                            Toast.makeText(
                                this@MainActivity,
                                "Hata: ${exception.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }
    }

    private fun applyBrightness(progress: Int) {
        window.attributes = window.attributes.also {
            it.screenBrightness = (progress / 100f).coerceIn(0.01f, 1.0f)
        }
    }

    override fun onResume() {
        super.onResume()
        orientationListener.enable()
        btnCapture.isEnabled = true
        brightnessSeekBar.progress = 50
        applyBrightness(50)

        if (shouldResetOnResume) {
            shouldResetOnResume = false
            spinnerReady = false
            vehicleSpinner.setSelection(0)
            selectedFuseboxId = null
            tvSelectedFusebox.visibility = View.GONE
            ResultActivity.pendingBitmap?.recycle()
            ResultActivity.pendingBitmap = null
            ResultActivity.pendingDetections = emptyList()
        }
    }

    override fun onPause() {
        super.onPause()
        orientationListener.disable()
        window.attributes = window.attributes.also {
            it.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        captureExecutor.shutdown()
        YoloDetector.releaseInstance()
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
                ).build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .setResolutionSelector(resolutionSelector)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
            )

            val hasFlash = camera?.cameraInfo?.hasFlashUnit() ?: false
            btnFlash.visibility = if (hasFlash) View.VISIBLE else View.GONE

            setupPinchToZoom()

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

    private fun setupPinchToZoom() {
        val scaleGestureDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val zoomState = camera?.cameraInfo?.zoomState?.value ?: return true
                    val newZoom = (zoomState.zoomRatio * detector.scaleFactor)
                        .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                    camera?.cameraControl?.setZoomRatio(newZoom)
                    val progress = ((newZoom - zoomState.minZoomRatio) /
                            (zoomState.maxZoomRatio - zoomState.minZoomRatio) * 100).toInt()
                    zoomSeekBar.progress = progress
                    return true
                }
            }
        )
        previewView.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            view.performClick()
            true
        }
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