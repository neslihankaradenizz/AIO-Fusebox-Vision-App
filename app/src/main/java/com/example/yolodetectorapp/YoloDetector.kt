package com.example.yolodetectorapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class Detection(
    val classId: Int,
    val className: String,
    val score: Float,
    val box: RectF
)

class YoloDetector(context: Context) {

    companion object {
        private const val MODEL_FILE   = "fusebox_model_float32.tflite"
        private const val INPUT_SIZE   = 1024
        private const val SCORE_THRESH = 0.25f

        private const val IOU_THRESH   = 0.45f
        private const val NUM_CLASSES  = 10 //sinif sayisi
        private const val NUM_DETS     = 21504

        val LABELS = listOf(
            "10_amp",   // 0
            "15_amp",   // 1
            "20_amp",   // 2
            "25_amp",   // 3
            "2_amp",    // 4
            "30_amp",   // 5
            "3_amp",
            "5_amp",    // 7
            "7.5_amp",  // 8
            "empty"     // 9
        )
    }

    private val interpreter: Interpreter

    init {
        val options = Interpreter.Options()
        options.setNumThreads(4)
        interpreter = Interpreter(loadModelFile(context), options)
    }
    private fun letterboxBitmap(src: Bitmap): Pair<Bitmap, FloatArray> {
        val scale = INPUT_SIZE.toFloat() / maxOf(src.width, src.height)
        val scaledW = (src.width  * scale).toInt()
        val scaledH = (src.height * scale).toInt()
        val padX = (INPUT_SIZE - scaledW) / 2
        val padY = (INPUT_SIZE - scaledH) / 2

        val result = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(android.graphics.Color.BLACK) // padding siyah
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        canvas.drawBitmap(scaled, padX.toFloat(), padY.toFloat(), null)
        scaled.recycle()

        // padding ve scale bilgisini geri dön (koordinat düzeltmesi için)
        return result to floatArrayOf(scale, padX.toFloat(), padY.toFloat())
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        if (bitmap.isRecycled) return emptyList()
        return try {
            val (letterboxed, meta) = letterboxBitmap(bitmap)
            val scale = meta[0]; val padX = meta[1]; val padY = meta[2]

            val inputBuf = bitmapToByteBuffer(letterboxed)
            val output = Array(1) { Array(4 + NUM_CLASSES) { FloatArray(NUM_DETS) } }
            synchronized(this) { interpreter.run(inputBuf, output) }

            // Tensor shape kontrolü
            android.util.Log.d("YOLO", "Output shape: ${output[0].size} x ${output[0][0].size}")

            parseOutput(output[0], scale, padX, padY)
        } catch (e: Exception) {
            android.util.Log.e("YOLO", "Detection error: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseOutput(
        raw: Array<FloatArray>,
        scale: Float, padX: Float, padY: Float
    ): List<Detection> {
        val candidates = mutableListOf<Detection>()

        // İlk 5'i logla
        for (det in 0 until minOf(5, NUM_DETS)) {
            var maxS = 0f; var cls = -1
            for (c in 0 until NUM_CLASSES) {
                val s = raw[c + 4][det]
                if (s > maxS) { maxS = s; cls = c }
            }
            android.util.Log.d("YOLO_RAW",
                "det=$det cls=$cls score=$maxS cx=${raw[0][det]} cy=${raw[1][det]}")
        }

        val contentW = INPUT_SIZE - 2f * padX
        val contentH = INPUT_SIZE - 2f * padY

        for (det in 0 until NUM_DETS) {
            var maxScore = 0f
            var classId  = -1
            for (c in 0 until NUM_CLASSES) {
                val s = raw[c + 4][det]
                if (s > maxScore) { maxScore = s; classId = c }
            }
            if (maxScore < SCORE_THRESH || classId < 0) continue

            // ✅ Model 0-1 normalize çıkarıyor → önce piksel'e çevir, sonra padding'i çıkar
            val pixCx = raw[0][det] * INPUT_SIZE
            val pixCy = raw[1][det] * INPUT_SIZE
            val pixW  = raw[2][det] * INPUT_SIZE
            val pixH  = raw[3][det] * INPUT_SIZE

            val nCx = ((pixCx - padX) / contentW).coerceIn(0f, 1f)
            val nCy = ((pixCy - padY) / contentH).coerceIn(0f, 1f)
            val nW  = (pixW  / contentW).coerceIn(0f, 1f)
            val nH  = (pixH  / contentH).coerceIn(0f, 1f)

            val box = RectF(
                (nCx - nW / 2f).coerceIn(0f, 1f),
                (nCy - nH / 2f).coerceIn(0f, 1f),
                (nCx + nW / 2f).coerceIn(0f, 1f),
                (nCy + nH / 2f).coerceIn(0f, 1f)
            )

            // Padding bölgesinde kalan (siyah alan) kutuları at
            if (box.width() < 0.001f || box.height() < 0.001f) continue

            candidates.add(Detection(classId, LABELS[classId], maxScore, box))
        }

        android.util.Log.d("YOLO", "Candidates before NMS: ${candidates.size}")
        return applyNMS(candidates)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        val result = mutableListOf<Detection>()
        val sorted = detections.sortedByDescending { it.score }
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val a = sorted[i]
            result.add(a)
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                if (iou(a.box, sorted[j].box) > IOU_THRESH) {
                    suppressed[j] = true
                }
            }
        }
        return result
    }
    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)
        val interArea = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)
        if (aArea + bArea - interArea <= 0f) return 0f
        return interArea / (aArea + bArea - interArea)
    }
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // Size: 1 * 1024 * 1024 * 3 * 4 = 12,582,912 bytes
        val buf = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buf.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buf.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buf.putFloat(((pixel shr 8) and 0xFF) / 255f)
            buf.putFloat((pixel and 0xFF) / 255f)
        }
        buf.rewind()
        return buf
    }
    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fd.fileDescriptor)
        return inputStream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }
    fun close() = interpreter.close()
}
