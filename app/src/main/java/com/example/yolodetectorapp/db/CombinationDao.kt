package com.example.yolodetectorapp.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CombinationDao {

    @Query("SELECT * FROM valid_combinations")
    suspend fun getAll(): List<ValidCombination>

    @Query("SELECT * FROM valid_combinations WHERE combinationId = :combId")
    suspend fun getByCombinationId(combId: String): List<ValidCombination>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(combination: ValidCombination)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(combinations: List<ValidCombination>)

    @Delete
    suspend fun delete(combination: ValidCombination)

    @Query("DELETE FROM valid_combinations")
    suspend fun deleteAll()
}