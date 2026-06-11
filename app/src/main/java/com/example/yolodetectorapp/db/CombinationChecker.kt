package com.example.yolodetectorapp.db

import com.example.yolodetectorapp.Detection

object CombinationChecker {

    /**
     * Tüm kombinasyonları sırayla kontrol eder.
     * @return Eşleşen kombinasyonun ID'si, yoksa null (NOK)
     */
    fun check(
        detections: List<Detection>,
        validEntries: List<ValidCombination>,
        combinationId: String? = null   // ← null = hepsini kontrol et
    ): String? {

        if (detections.isEmpty()) return null

        val totalPositions = 18
        val colSize = totalPositions / 2  // 9

        val sortedByX = detections.sortedBy { (it.box.left + it.box.right) / 2f }
        val count = sortedByX.size
        val half = if (count >= totalPositions) colSize else count / 2

        val leftCol = sortedByX.take(half)
            .sortedBy { (it.box.top + it.box.bottom) / 2f }
        val rightCol = sortedByX.drop(half)
            .sortedBy { (it.box.top + it.box.bottom) / 2f }

        val detectedIds = (leftCol + rightCol).map { it.classId }

        android.util.Log.d("YOLO", "Tespit edilen sıra ($count adet): $detectedIds")

        val entriesToCheck = if (combinationId != null)
            validEntries.filter { it.combinationId == combinationId }
        else
            validEntries

        // Sırayla dön, ilk tam eşleşende combinationId döndür
        for (entry in entriesToCheck) {
            val dbIds = entry.objectIds
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }

            if (dbIds == detectedIds) {
                android.util.Log.d(
                    "YOLO",
                    "Eşleşme bulundu! combinationId=${entry.combinationId}, dbId=${entry.id}"
                )
                return entry.combinationId  // ← OK, hangi kombinasyon olduğunu döner
            }
        }

        android.util.Log.d("YOLO", "Hiçbir kombinasyon eşleşmedi → NOK")
        return null  // ← NOK
    }
}