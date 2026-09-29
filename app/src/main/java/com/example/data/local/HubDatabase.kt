package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "closed_candle_signal_logs")
data class SavedSignalSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbolWithSuffix: String,
    val timeframeLabel: String,
    val closedBarEpochSec: Long,
    val closePrice: Double,
    val verdictLabel: String,
    val patternDetected: String,
    val structureLabel: String,
    val trendLabel: String,
    val rsi14: Double,
    val adx14: Double,
    val atr14: Double,
    val stopReferencePrice: Double,
    val targetReferencePrice: Double,
    val confluenceScore: Int,
    val savedTimestampMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_macro_events")
data class CustomNewsEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val categoryTag: String,
    val targetSymbolCode: String, // "XAUUSD" or "EURUSD" or "BOTH"
    val impactLevel: String,      // "HIGH", "MEDIUM", "LOW"
    val sentimentCode: String,
    val takeawayNote: String,
    val createdTimeEpochSec: Long = System.currentTimeMillis() / 1000L
)

@Dao
interface HubDao {
    @Query("SELECT * FROM closed_candle_signal_logs ORDER BY savedTimestampMs DESC")
    fun observeSignalLogs(): Flow<List<SavedSignalSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignalLog(entity: SavedSignalSnapshotEntity)

    @Query("DELETE FROM closed_candle_signal_logs WHERE id = :id")
    suspend fun deleteSignalLogById(id: Int)

    @Query("DELETE FROM closed_candle_signal_logs")
    suspend fun clearAllSignalLogs()

    @Query("SELECT * FROM custom_macro_events ORDER BY createdTimeEpochSec DESC")
    fun observeCustomNewsEvents(): Flow<List<CustomNewsEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomNewsEvent(entity: CustomNewsEventEntity)

    @Query("DELETE FROM custom_macro_events WHERE id = :id")
    suspend fun deleteCustomNewsEventById(id: Int)
}

@Database(
    entities = [SavedSignalSnapshotEntity::class, CustomNewsEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HubDatabase : RoomDatabase() {
    abstract fun hubDao(): HubDao

    companion object {
        @Volatile
        private var INSTANCE: HubDatabase? = null

        fun getInstance(context: Context): HubDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HubDatabase::class.java,
                    "hub_smart_pattern_engine.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
