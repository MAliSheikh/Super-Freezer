package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

class AppFreezerRepository(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppFreezerDatabase::class.java,
        "app_freezer_database"
    ).build()

    private val dao = db.dao()

    fun getAllAppStatesFlow(): Flow<List<AppFrozenState>> = dao.getAllAppStatesFlow()

    suspend fun getAllAppStates(): List<AppFrozenState> = dao.getAllAppStates()

    suspend fun getAppState(packageName: String): AppFrozenState? = dao.getAppState(packageName)

    suspend fun insertOrUpdateAppState(state: AppFrozenState) = dao.insertOrUpdateAppState(state)

    suspend fun deleteAppState(packageName: String) = dao.deleteAppState(packageName)

    fun getRecentLogsFlow(): Flow<List<FreezeLog>> = dao.getRecentLogsFlow()

    suspend fun insertLog(log: FreezeLog) = dao.insertLog(log)

    suspend fun clearAllLogs() = dao.clearAllLogs()

    suspend fun getSetting(key: String, defaultValue: String): String {
        return dao.getSettingValue(key) ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) {
        dao.saveSetting(FreezerSetting(key, value))
    }
}
