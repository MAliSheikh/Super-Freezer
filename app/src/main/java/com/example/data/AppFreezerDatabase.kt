package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_states")
data class AppFrozenState(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isFrozen: Boolean = false,
    val isWhitelisted: Boolean = false,
    val isBlacklisted: Boolean = false,
    val lastUsedTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "freeze_logs")
data class FreezeLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val method: String, // "Accessibility", "Manual", "Simulation"
    val success: Boolean = true
)

@Entity(tableName = "freezer_settings")
data class FreezerSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface AppFreezerDao {
    @Query("SELECT * FROM app_states")
    fun getAllAppStatesFlow(): Flow<List<AppFrozenState>>

    @Query("SELECT * FROM app_states")
    suspend fun getAllAppStates(): List<AppFrozenState>

    @Query("SELECT * FROM app_states WHERE packageName = :packageName")
    suspend fun getAppState(packageName: String): AppFrozenState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAppState(state: AppFrozenState)

    @Query("DELETE FROM app_states WHERE packageName = :packageName")
    suspend fun deleteAppState(packageName: String)

    @Query("SELECT * FROM freeze_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogsFlow(): Flow<List<FreezeLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: FreezeLog)

    @Query("DELETE FROM freeze_logs")
    suspend fun clearAllLogs()

    @Query("SELECT * FROM freezer_settings")
    suspend fun getAllSettings(): List<FreezerSetting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: FreezerSetting)

    @Query("SELECT value FROM freezer_settings WHERE `key` = :key")
    suspend fun getSettingValue(key: String): String?
}

@Database(entities = [AppFrozenState::class, FreezeLog::class, FreezerSetting::class], version = 1, exportSchema = false)
abstract class AppFreezerDatabase : RoomDatabase() {
    abstract fun dao(): AppFreezerDao
}
