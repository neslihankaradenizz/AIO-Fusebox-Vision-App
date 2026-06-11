package com.example.yolodetectorapp.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

@Database(entities = [ValidCombination::class], version =5, exportSchema = false)
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
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            try {
                                // assets/combination.json oku
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

                                    // Pozisyonları sıraya göre al (position 1→N)
                                    val positionCount = expected.length()
                                    val classIds = Array(positionCount) { 0 }

                                    for (j in 0 until positionCount) {
                                        val item = expected.getJSONObject(j)
                                        val position = item.getInt("position") // 1-based
                                        val classId = item.getInt("classId")
                                        classIds[position - 1] = classId
                                    }

                                    val objectIdsString = classIds.joinToString(",")

                                    db.execSQL(
                                        "INSERT INTO valid_combinations (combinationId, objectIds) VALUES (?, ?)",
                                        arrayOf(combinationId, objectIdsString)
                                    )

                                    android.util.Log.d(
                                        "YOLO",
                                        "DB'ye eklendi [$combinationId]: $objectIdsString"
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("YOLO", "DB seed hatası: ${e.message}", e)
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}