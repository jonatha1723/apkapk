package com.example

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "access_keys")
data class AccessKey(
    @PrimaryKey
    val keyValue: String,
    val deviceId: String? = null,
    val isBanned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface KeyDao {
    @Query("SELECT * FROM access_keys")
    fun getAllKeys(): Flow<List<AccessKey>>

    @Query("SELECT * FROM access_keys WHERE keyValue = :key LIMIT 1")
    suspend fun getKey(key: String): AccessKey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(accessKey: AccessKey)

    @Update
    suspend fun updateKey(accessKey: AccessKey)

    @Delete
    suspend fun deleteKey(accessKey: AccessKey)
}

@Database(entities = [AccessKey::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun keyDao(): KeyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
