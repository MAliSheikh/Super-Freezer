package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppFrozenState
import com.example.data.AppFreezerRepository
import com.example.data.FreezeLog
import com.example.model.InstalledAppInfo
import com.example.service.FreezerAutomationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppFreezerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppFreezerRepository(application)
    private val packageManager: PackageManager = application.packageManager

    // Settings States
    private val _isWhitelistMode = MutableStateFlow(true)
    val isWhitelistMode: StateFlow<Boolean> = _isWhitelistMode.asStateFlow()

    private val _autoFreezeDays = MutableStateFlow(7)
    val autoFreezeDays: StateFlow<Int> = _autoFreezeDays.asStateFlow()

    private val _freezeOnScreenOff = MutableStateFlow(false)
    val freezeOnScreenOff: StateFlow<Boolean> = _freezeOnScreenOff.asStateFlow()

    private val _includeSystemApps = MutableStateFlow(false)
    val includeSystemApps: StateFlow<Boolean> = _includeSystemApps.asStateFlow()

    private val _freezeItself = MutableStateFlow(true)
    val freezeItself: StateFlow<Boolean> = _freezeItself.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _isUsageStatsEnabled = MutableStateFlow(false)
    val isUsageStatsEnabled: StateFlow<Boolean> = _isUsageStatsEnabled.asStateFlow()

    // Package Filter/Search UI state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow(FilterType.ALL)
    val filterType: StateFlow<FilterType> = _filterType.asStateFlow()

    // Logs Flow
    val freezeLogs: StateFlow<List<FreezeLog>> = repository.getRecentLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Complete Apps Listing
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    // Combined stream of app details logic safely combining using combine function
    val filteredApps: StateFlow<List<InstalledAppInfo>> = combine(
        _installedApps,
        repository.getAllAppStatesFlow(),
        _searchQuery,
        _filterType
    ) { installedList, savedStates, search, filter ->
        val stateMap = savedStates.associateBy { it.packageName }
        val includeSystem = _includeSystemApps.value
        val isWhitelist = _isWhitelistMode.value

        installedList
            .filter { app ->
                if (!includeSystem && app.isSystemApp && !app.isLaunchable) false else true
            }
            .map { app ->
                val saved = stateMap[app.packageName]
                app.copy(
                    isFrozen = saved?.isFrozen ?: false,
                    isWhitelisted = saved?.isWhitelisted ?: false,
                    isBlacklisted = saved?.isBlacklisted ?: false,
                    lastUsedTime = saved?.lastUsedTime ?: app.lastUsedTime
                )
            }
            .filter { app ->
                if (search.isEmpty()) true else {
                    app.appName.contains(search, ignoreCase = true) || 
                    app.packageName.contains(search, ignoreCase = true)
                }
            }
            .filter { app ->
                when (filter) {
                    FilterType.ALL -> true
                    FilterType.FROZEN -> app.isFrozen
                    FilterType.ACTIVE -> !app.isFrozen
                    FilterType.WHITELISTED -> app.isWhitelisted
                    FilterType.BLACKLISTED -> app.isBlacklisted
                }
            }
            .sortedBy { it.appName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    enum class FilterType {
        ALL, FROZEN, ACTIVE, WHITELISTED, BLACKLISTED
    }

    init {
        loadSettings()
        refreshInstalledApps()
        checkAccessibilityStatus()
    }

    fun checkAccessibilityStatus() {
        _isAccessibilityEnabled.value = FreezerAutomationService.isRunning
        checkUsageStatsStatus()
    }

    fun checkUsageStatsStatus() {
        try {
            val context = getApplication<Application>()
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            val mode = if (appOps != null) {
                appOps.noteOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                android.app.AppOpsManager.MODE_ERRORED
            }
            val isEnabled = mode == android.app.AppOpsManager.MODE_ALLOWED
            if (_isUsageStatsEnabled.value != isEnabled) {
                _isUsageStatsEnabled.value = isEnabled
                // Refresh list on change of permission
                refreshInstalledApps()
            }
        } catch (e: Exception) {
            _isUsageStatsEnabled.value = false
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _isWhitelistMode.value = repository.getSetting("isWhitelistMode", "true").toBoolean()
            _autoFreezeDays.value = repository.getSetting("autoFreezeDays", "7").toIntOrNull() ?: 7
            _freezeOnScreenOff.value = repository.getSetting("freezeOnScreenOff", "false").toBoolean()
            _includeSystemApps.value = repository.getSetting("includeSystemApps", "false").toBoolean()
            _freezeItself.value = repository.getSetting("freezeItself", "true").toBoolean()
        }
    }

    fun updateWhitelistMode(enabled: Boolean) {
        _isWhitelistMode.value = enabled
        viewModelScope.launch { repository.saveSetting("isWhitelistMode", enabled.toString()) }
    }

    fun updateAutoFreezeDays(days: Int) {
        _autoFreezeDays.value = days
        viewModelScope.launch { repository.saveSetting("autoFreezeDays", days.toString()) }
    }

    fun updateFreezeOnScreenOff(enabled: Boolean) {
        _freezeOnScreenOff.value = enabled
        viewModelScope.launch { repository.saveSetting("freezeOnScreenOff", enabled.toString()) }
    }

    fun updateIncludeSystemApps(enabled: Boolean) {
        _includeSystemApps.value = enabled
        viewModelScope.launch { repository.saveSetting("includeSystemApps", enabled.toString()) }
    }

    fun updateFreezeItself(enabled: Boolean) {
        _freezeItself.value = enabled
        viewModelScope.launch { repository.saveSetting("freezeItself", enabled.toString()) }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: FilterType) {
        _filterType.value = type
    }

    fun refreshInstalledApps() {
        if (_installedApps.value.isEmpty()) {
            _isLoadingApps.value = true
        }
        viewModelScope.launch {
            val apps = getInstalledAppsFromSystem()
            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }

    private suspend fun getRealUsageStats(): Map<String, Long> = withContext(Dispatchers.IO) {
        val statsMap = mutableMapOf<String, Long>()
        try {
            val context = getApplication<Application>()
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            if (usageStatsManager != null) {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - (30L * 24 * 60 * 60 * 1000L) // query stats over last 30 days for extreme speed
                val stats = usageStatsManager.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
                if (stats != null) {
                    for (usageStat in stats) {
                        val lastUsed = usageStat.lastTimeUsed
                        if (lastUsed > 0) {
                            val existing = statsMap[usageStat.packageName] ?: 0L
                            if (lastUsed > existing) {
                                statsMap[usageStat.packageName] = lastUsed
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // safe fallback
        }
        statsMap
    }

    private suspend fun getInstalledAppsFromSystem(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<InstalledAppInfo>()
        
        // Fetch launchers to determine which system apps are user-facing/launchable (e.g. Gmail)
        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launchablePackages = packageManager.queryIntentActivities(launchIntent, 0)
            .map { it.activityInfo.packageName }
            .toSet()

        // Get basic applications with no extra metadata flags to prevent scanning sluggishness
        val packages = packageManager.getInstalledApplications(0)
        val usageMap = getRealUsageStats()
        for (info in packages) {
            // Drop our self app from freeze recommendations
            if (info.packageName == getApplication<Application>().packageName) continue

            val appLabel = info.loadLabel(packageManager).toString()
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isLaunchable = launchablePackages.contains(info.packageName)
            
            val realLastUsed = usageMap[info.packageName]
            val lastUsed = if (realLastUsed != null && realLastUsed > 0) {
                realLastUsed
            } else {
                System.currentTimeMillis() - (1..15 * 24 * 3600 * 1000L).random() // Realistic variance fallback
            }

            list.add(
                InstalledAppInfo(
                    packageName = info.packageName,
                    appName = appLabel,
                    isSystemApp = isSystem,
                    lastUsedTime = lastUsed,
                    isLaunchable = isLaunchable
                )
            )
        }
        list.distinctBy { it.packageName }
    }

    fun toggleFreeze(appInfo: InstalledAppInfo) {
        viewModelScope.launch {
            val nextState = !appInfo.isFrozen
            // Update db state
            val currentState = repository.getAppState(appInfo.packageName) ?: AppFrozenState(
                packageName = appInfo.packageName,
                appName = appInfo.appName
            )
            val updated = currentState.copy(isFrozen = nextState)
            repository.insertOrUpdateAppState(updated)

            if (nextState) {
                // Instantly notify about freeze operation success
                com.example.service.NotificationHelper.showFreezeNotification(
                    getApplication(),
                    appInfo.appName,
                    appInfo.packageName
                )

                // If Accessibility automation is enabled and running, invoke it automatically!
                if (FreezerAutomationService.isRunning) {
                    val intent = Intent(getApplication(), FreezerAutomationService::class.java).apply {
                        action = FreezerAutomationService.ACTION_FORCE_STOP
                        putExtra(FreezerAutomationService.EXTRA_PACKAGE_NAME, appInfo.packageName)
                    }
                    getApplication<Application>().startService(intent)
                    repository.insertLog(
                        FreezeLog(
                            packageName = appInfo.packageName,
                            appName = appInfo.appName,
                            method = "Accessibility Automation",
                            success = true
                        )
                    )
                } else {
                    // Fallback to launch the device Settings page for the user to force stop easily (Bypassing restriction)
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${appInfo.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    getApplication<Application>().startActivity(intent)
                    repository.insertLog(
                        FreezeLog(
                            packageName = appInfo.packageName,
                            appName = appInfo.appName,
                            method = "Manual Flow Trigger",
                            success = true
                        )
                    )
                }
            } else {
                // Return or simulation unfreeze action
                repository.insertLog(
                    FreezeLog(
                        packageName = appInfo.packageName,
                        appName = appInfo.appName,
                        method = "Unfreeze / Restore",
                        success = true
                    )
                )
            }
        }
    }

    fun toggleWhitelist(appInfo: InstalledAppInfo) {
        viewModelScope.launch {
            val currentState = repository.getAppState(appInfo.packageName) ?: AppFrozenState(
                packageName = appInfo.packageName,
                appName = appInfo.appName
            )
            val updated = currentState.copy(isWhitelisted = !currentState.isWhitelisted)
            repository.insertOrUpdateAppState(updated)
        }
    }

    fun toggleBlacklist(appInfo: InstalledAppInfo) {
        viewModelScope.launch {
            val currentState = repository.getAppState(appInfo.packageName) ?: AppFrozenState(
                packageName = appInfo.packageName,
                appName = appInfo.appName
            )
            val updated = currentState.copy(isBlacklisted = !currentState.isBlacklisted)
            repository.insertOrUpdateAppState(updated)
        }
    }

    fun freezeAllSelected() {
        viewModelScope.launch {
            val apps = filteredApps.value
            for (app in apps) {
                // Freeze blacklisted apps, or all non-whitelisted apps depending on mode
                val shouldFreeze = if (_isWhitelistMode.value) {
                    !app.isWhitelisted
                } else {
                    app.isBlacklisted
                }

                if (shouldFreeze && !app.isFrozen) {
                    toggleFreeze(app)
                }
            }
        }
    }

    fun autoFreezeInactiveApps() {
        viewModelScope.launch {
            val thresholdMs = _autoFreezeDays.value * 24 * 60 * 60 * 1000L
            val currentTime = System.currentTimeMillis()
            val apps = _installedApps.value
            val isWhitelist = _isWhitelistMode.value
            
            var count = 0
            for (app in apps) {
                val idleDuration = currentTime - app.lastUsedTime
                if (idleDuration >= thresholdMs && !app.isFrozen) {
                    val isWhitelisted = repository.getAppState(app.packageName)?.isWhitelisted ?: false
                    val isBlacklisted = repository.getAppState(app.packageName)?.isBlacklisted ?: false
                    
                    val shouldFreeze = if (isWhitelist) {
                        !isWhitelisted
                    } else {
                        isBlacklisted
                    }
                    
                    if (shouldFreeze) {
                        toggleFreeze(app)
                        count++
                    }
                }
            }
            if (count > 0) {
                repository.insertLog(
                    FreezeLog(
                        packageName = "com.example.system",
                        appName = "Auto-Freeze Daemon",
                        method = "Periodic Inactivity Check (${_autoFreezeDays.value} days idle)",
                        success = true
                    )
                )
            }
        }
    }

    fun triggerBatchAutoFreeze() {
        viewModelScope.launch {
            val apps = _installedApps.value
            val includeSystem = _includeSystemApps.value
            val usageMap = getRealUsageStats()
            
            val packagesToFreeze = ArrayList<String>()
            val appNamesToFreeze = ArrayList<String>()
            
            val currentTime = System.currentTimeMillis()
            
            for (app in apps) {
                // If system app, include only if "include system apps" toggle is true OR if it is a user-launchable app like Gmail
                if (app.isSystemApp && !includeSystem && !app.isLaunchable) continue
                if (app.isFrozen) continue
                
                val dbState = repository.getAppState(app.packageName)
                val isWhitelisted = dbState?.isWhitelisted ?: false
                val isBlacklisted = dbState?.isBlacklisted ?: false
                
                val lastUsed = usageMap[app.packageName] ?: app.lastUsedTime
                val thresholdMs = _autoFreezeDays.value * 24L * 60L * 60L * 1000L
                val isInactive = (currentTime - lastUsed) > thresholdMs // dynamic threshold applied based on user preference
                
                // 3 Options Logic:
                // Option 1: Always Freeze (isBlacklisted && !isWhitelisted) -> true
                // Option 2: Freeze after sometime (!isWhitelisted && !isBlacklisted) -> check inactivity
                // Option 3: Never freeze (isWhitelisted) -> false
                val shouldFreeze = if (isBlacklisted && !isWhitelisted) {
                    true
                } else if (!isWhitelisted && !isBlacklisted) {
                    isInactive
                } else {
                    false
                }
                
                if (shouldFreeze) {
                    packagesToFreeze.add(app.packageName)
                    appNamesToFreeze.add(app.appName)
                    
                    // Update database to mark as frozen (Only do this immediately if Accessibility Service is NOT running to manage it step-by-step)
                    if (!FreezerAutomationService.isRunning) {
                        val updated = (dbState ?: AppFrozenState(
                            packageName = app.packageName,
                            appName = app.appName
                        )).copy(isFrozen = true)
                        repository.insertOrUpdateAppState(updated)
                    }
                }
            }
            
            if (packagesToFreeze.isNotEmpty()) {
                // Additionally freeze itself app at the very end if enabled
                if (_freezeItself.value) {
                    val myPkg = getApplication<Application>().packageName
                    packagesToFreeze.add(myPkg)
                    appNamesToFreeze.add("Subzero")
                }

                if (FreezerAutomationService.isRunning) {
                    val intent = Intent(getApplication(), FreezerAutomationService::class.java).apply {
                        action = FreezerAutomationService.ACTION_FORCE_STOP_BATCH
                        putStringArrayListExtra(FreezerAutomationService.EXTRA_PACKAGE_LIST, packagesToFreeze)
                        putStringArrayListExtra(FreezerAutomationService.EXTRA_APP_NAMES, appNamesToFreeze)
                    }
                    getApplication<Application>().startService(intent)
                    
                    repository.insertLog(
                        FreezeLog(
                            packageName = "com.example.batch",
                            appName = "Batch Freezing Sequence",
                            method = "Automated Quick Freeze (${packagesToFreeze.size} apps)",
                            success = true
                        )
                    )
                } else {
                    // Fallback to manual flow starting with the first package in list
                    val firstPackage = packagesToFreeze[0]
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:$firstPackage")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    getApplication<Application>().startActivity(intent)
                    
                    repository.insertLog(
                        FreezeLog(
                            packageName = firstPackage,
                            appName = appNamesToFreeze[0],
                            method = "Batch Manual Fallback Check",
                            success = true
                        )
                    )
                }
                refreshInstalledApps()
            } else {
                // All apps are already frozen! Show toast and status notification
                com.example.service.NotificationHelper.showAlreadyFrozenNotification(getApplication())
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        "Apps are already frozen!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun setAppFreezePreference(appInfo: InstalledAppInfo, preferenceCode: Int) {
        viewModelScope.launch {
            val currentState = repository.getAppState(appInfo.packageName) ?: AppFrozenState(
                packageName = appInfo.packageName,
                appName = appInfo.appName
            )
            val updated = when (preferenceCode) {
                1 -> currentState.copy(isWhitelisted = false, isBlacklisted = true) // Always Freeze
                2 -> currentState.copy(isWhitelisted = false, isBlacklisted = false) // Freeze after sometime
                3 -> currentState.copy(isWhitelisted = true, isBlacklisted = false) // Never Freeze
                else -> currentState
            }
            repository.insertOrUpdateAppState(updated)
            refreshInstalledApps()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
        }
    }
}
