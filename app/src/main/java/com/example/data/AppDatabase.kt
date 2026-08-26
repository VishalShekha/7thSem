package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "requests")
data class EmergencyRequestEntity(
    @PrimaryKey val id: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val status: String,
    val severity: Int,
    val reporterId: String
)

@Dao
interface RequestDao {
    @Query("SELECT * FROM requests")
    fun getAllRequests(): Flow<List<EmergencyRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: EmergencyRequestEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequests(requests: List<EmergencyRequestEntity>)
}

@Database(entities = [EmergencyRequestEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun requestDao(): RequestDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meshlink_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
