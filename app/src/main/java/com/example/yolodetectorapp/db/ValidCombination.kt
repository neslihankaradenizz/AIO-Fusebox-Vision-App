package com.example.yolodetectorapp.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Her geçerli kombinasyon için:
 * - combinationId: JSON'daki "id" (ör. "fusebox_v3")
 * - objectIds: sıralı classId listesi, virgülle ayrılmış string
 *   (pozisyon 1–18 sırasıyla: sol kolon 1–9, sağ kolon 10–18)
 */
@Entity(tableName = "valid_combinations")
data class ValidCombination(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val combinationId: String,   // ör. "fusebox_v3"
    val objectIds: String        // ör. "8,5,1,8,2,2,3,6,4,4,1,5,8,6,7,8,7,8"
)