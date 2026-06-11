package com.example.yolodetectorapp.db

import com.example.yolodetectorapp.Detection

object CombinationChecker {

    fun check(
        detections: List<Detection>,
        validEntries: List<ValidCombination>,
        combinationId: String? = null
    ): String? {

        if (detections.isEmpty()) return null

        val sortedByX = detections.sortedBy { (it.box.left + it.box.right) / 2f }
        val splitIdx  = findColumnSplit(sortedByX)

        val leftCol  = sortedByX.take(splitIdx).sortedBy { (it.box.top + it.box.bottom) / 2f }
        val rightCol = sortedByX.drop(splitIdx).sortedBy { (it.box.top + it.box.bottom) / 2f }

        android.util.Log.d("YOLO", "=== KOLON DEBUG ===")
        android.util.Log.d("YOLO", "Sol kolon (küçük X) IDs: ${leftCol.map { it.classId }}")
        android.util.Log.d("YOLO", "Sağ kolon (büyük X) IDs: ${rightCol.map { it.classId }}")
        android.util.Log.d("YOLO", "Sol kolon Y merkezleri: ${leftCol.map { (it.box.top + it.box.bottom) / 2f }}")
        android.util.Log.d("YOLO", "Sağ kolon Y merkezleri: ${rightCol.map { (it.box.top + it.box.bottom) / 2f }}")

        val detectedIds = (leftCol + rightCol).map { it.classId }

        val entriesToCheck = if (combinationId != null)
            validEntries.filter { it.combinationId == combinationId }
        else
            validEntries

        for (entry in entriesToCheck) {
            val dbIds = entry.objectIds.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (dbIds == detectedIds) {
                android.util.Log.d("YOLO", "Eşleşme: ${entry.combinationId}")
                return entry.combinationId
            }

            android.util.Log.d("YOLO", "--- ${entry.combinationId} karşılaştırması ---")
            android.util.Log.d("YOLO", "DB     (${dbIds.size}): $dbIds")
            android.util.Log.d("YOLO", "Tespit (${detectedIds.size}): $detectedIds")

            if (dbIds.size == detectedIds.size) {
                val mismatches = dbIds.indices.filter { dbIds[it] != detectedIds[it] }
                android.util.Log.d("YOLO", "Uyumsuz pozisyonlar (0-indexed): $mismatches")
                mismatches.forEach { i ->
                    android.util.Log.d("YOLO", "  pos $i → DB=${dbIds[i]}  Tespit=${detectedIds[i]}")
                }
            } else {
                android.util.Log.d("YOLO", "SAYI UYUMSUZ: DB ${dbIds.size} ≠ Tespit ${detectedIds.size}")
            }
        }

        android.util.Log.d("YOLO", "Eşleşme yok → NOK")
        return null
    }

    private fun findColumnSplit(sortedByX: List<Detection>): Int {
        val n = sortedByX.size
        if (n <= 1) return n

        val xCenters = sortedByX.map { (it.box.left + it.box.right) / 2f }
        var maxGap  = -1f
        var splitIdx = n / 2

        for (i in 0 until n - 1) {
            val gap = xCenters[i + 1] - xCenters[i]
            if (gap > maxGap) { maxGap = gap; splitIdx = i + 1 }
        }
        return splitIdx
    }

}