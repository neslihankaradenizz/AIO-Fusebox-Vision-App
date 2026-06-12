package com.example.yolodetectorapp.db

import com.example.yolodetectorapp.Detection

data class MatchResult(
    val matchedId: String?,
    val expectedClassIds: List<Int>,
    val expectedCombinationId: String? = null
)

object CombinationChecker {

    fun check(
        detections: List<Detection>,
        validEntries: List<ValidCombination>,
        combinationId: String? = null
    ): String? = checkWithExpected(detections, validEntries, combinationId).matchedId

    fun checkWithExpected(
        detections: List<Detection>,
        validEntries: List<ValidCombination>,
        combinationId: String? = null
    ): MatchResult {
        if (detections.isEmpty()) return MatchResult(null, emptyList())

        val sortedByX = detections.sortedBy { (it.box.left + it.box.right) / 2f }
        val splitIdx  = findColumnSplit(sortedByX)
        val leftCol   = sortedByX.take(splitIdx).sortedBy { (it.box.top + it.box.bottom) / 2f }
        val rightCol  = sortedByX.drop(splitIdx).sortedBy { (it.box.top + it.box.bottom) / 2f }
        val rawIds = (leftCol + rightCol).map { it.classId }
        val detectedIds = if (rawIds.size < 18)
            rawIds + List(18 - rawIds.size) { 8 }
        else
            rawIds.take(18)

        val entriesToCheck = if (combinationId != null)
            validEntries.filter { it.combinationId == combinationId }
        else
            validEntries

        var bestEntry: ValidCombination? = null
        var bestScore = -1

        for (entry in entriesToCheck) {
            val dbIds = entry.objectIds.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (dbIds == detectedIds) {
                return MatchResult(entry.combinationId, dbIds, entry.combinationId)
            }
            if (dbIds.size == detectedIds.size) {
                val score = dbIds.indices.count { dbIds[it] == detectedIds[it] }
                if (score > bestScore) { bestScore = score; bestEntry = entry }
            }
        }

        val expectedIds = bestEntry?.objectIds
            ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList()

        android.util.Log.d("YOLO", "Eşleşme yok → NOK")
        return MatchResult(null, expectedIds, bestEntry?.combinationId)
    }

    private fun findColumnSplit(sortedByX: List<Detection>): Int {
        val n = sortedByX.size
        if (n <= 1) return n
        val xCenters = sortedByX.map { (it.box.left + it.box.right) / 2f }
        var maxGap = -1f
        var splitIdx = n / 2
        for (i in 0 until n - 1) {
            val gap = xCenters[i + 1] - xCenters[i]
            if (gap > maxGap) { maxGap = gap; splitIdx = i + 1 }
        }
        return splitIdx
    }
}