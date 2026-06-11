package com.example.yolodetectorapp.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject

@Database(entities = [ValidCombination::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun combinationDao(): CombinationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yolo_detector.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance

                GlobalScope.launch(Dispatchers.IO) {
                    val dao = instance.combinationDao()
                    if (dao.getAll().isEmpty()) {
                        seedDatabase(context, instance)
                    }
                }

                instance
            }
        }

        private fun seedDatabase(context: Context, db: AppDatabase) {
            try {
                val json = context.assets
                    .open("combinations.json")
                    .bufferedReader()
                    .use { it.readText() }

                val root = JSONObject(json)
                val combinations = root.getJSONArray("combinations")

                for (i in 0 until combinations.length()) {
                    val combo = combinations.getJSONObject(i)
                    val combinationId = combo.getString("id")
                    val expected = combo.getJSONArray("expected")

                    val positionCount = expected.length()
                    val classIds = Array(positionCount) { 0 }

                    for (j in 0 until positionCount) {
                        val item = expected.getJSONObject(j)
                        val position = item.getInt("position")
                        val classId = item.getInt("classId")
                        classIds[position - 1] = classId
                    }

                    val objectIdsString = classIds.joinToString(",")

                    kotlinx.coroutines.runBlocking {
                        db.combinationDao().insert(
                            ValidCombination(
                                combinationId = combinationId,
                                objectIds = objectIdsString
                            )
                        )
                    }

                    android.util.Log.d("YOLO", "DB'ye eklendi [$combinationId]: $objectIdsString")
                }
            } catch (e: Exception) {
                android.util.Log.e("YOLO", "Seed hatası: ${e.message}", e)
            }
        }
    }
}