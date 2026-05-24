package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import com.example.R
import com.example.data.AppFreezerRepository
import com.example.service.FreezerAutomationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FreezerWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_WIDGET_QUICK_FREEZE = "com.example.action.WIDGET_QUICK_FREEZE"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.freezer_widget_layout)

        // Broadcast pending intent to trigger batch freezing
        val intent = Intent(context, FreezerWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_QUICK_FREEZE
        }
        
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flag)
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_QUICK_FREEZE) {
            Log.d("FreezerWidget", "Quick freeze clicked from Home screen Widget")
            Toast.makeText(context, "❄️ Initializing Winterization Sequence...", Toast.LENGTH_SHORT).show()

            // Query database in background thread
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = AppFreezerRepository(context)
                    val dbStates = repository.getAllAppStates().associateBy { it.packageName }
                    
                    val includeSystem = repository.getSetting("includeSystemApps", "false").toBoolean()

                    val pm = context.packageManager
                    
                    // Fetch launchers to determine which system apps are user-facing/launchable (e.g. Gmail)
                    val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    val launchablePackages = pm.queryIntentActivities(launchIntent, 0)
                        .map { it.activityInfo.packageName }
                        .toSet()

                    // Use fast package fetch with no heavy metadata
                    val installedApps = pm.getInstalledApplications(0)

                    // Get Usage Stats to check inactivity
                    val usageMap = HashMap<String, Long>()
                    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                    if (usageStatsManager != null) {
                        val endTime = System.currentTimeMillis()
                        val startTime = endTime - (30L * 24 * 60 * 60 * 1000L) // last 30 days
                        val stats = usageStatsManager.queryUsageStats(
                            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                            startTime,
                            endTime
                        )
                        if (stats != null) {
                            for (st in stats) {
                                val existing = usageMap[st.packageName] ?: 0L
                                if (st.lastTimeUsed > existing) {
                                    usageMap[st.packageName] = st.lastTimeUsed
                                }
                            }
                        }
                    }

                    val packagesToFreeze = ArrayList<String>()
                    val appNamesToFreeze = ArrayList<String>()
                    val currentTime = System.currentTimeMillis()

                    for (info in installedApps) {
                        // Skip our own package
                        if (info.packageName == context.packageName) continue
                        
                        val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val isLaunchable = launchablePackages.contains(info.packageName)
                        
                        if (isSystem && !includeSystem && !isLaunchable) continue

                        val dbState = dbStates[info.packageName]
                        val isFrozen = dbState?.isFrozen ?: false
                        val isWhitelisted = dbState?.isWhitelisted ?: false
                        val isBlacklisted = dbState?.isBlacklisted ?: false

                        val lastUsed = usageMap[info.packageName] ?: (dbState?.lastUsedTime ?: 0L)
                        val isInactive = (currentTime - lastUsed) > (3 * 60 * 1000L) // 3 minutes idle threshold

                        // 3 Options Logic
                        val shouldFreeze = if (isBlacklisted && !isWhitelisted) {
                            true // Option 1: Always Freeze
                        } else if (!isWhitelisted && !isBlacklisted) {
                            isInactive // Option 2: Freeze after sometime if inactive
                        } else {
                            false // Option 3: Never Freeze
                        }

                        if (shouldFreeze && !isFrozen) {
                            packagesToFreeze.add(info.packageName)
                            val appLabel = info.loadLabel(pm).toString()
                            appNamesToFreeze.add(appLabel)

                            // Update DB state
                            val updated = (dbState ?: com.example.data.AppFrozenState(
                                packageName = info.packageName,
                                appName = appLabel
                            )).copy(isFrozen = true)
                            repository.insertOrUpdateAppState(updated)
                        }
                    }

                    if (packagesToFreeze.isNotEmpty()) {
                        if (FreezerAutomationService.isRunning) {
                            // Forward batch to accessibility service
                            val serviceIntent = Intent(context, FreezerAutomationService::class.java).apply {
                                action = FreezerAutomationService.ACTION_FORCE_STOP_BATCH
                                putStringArrayListExtra(FreezerAutomationService.EXTRA_PACKAGE_LIST, packagesToFreeze)
                                putStringArrayListExtra(FreezerAutomationService.EXTRA_APP_NAMES, appNamesToFreeze)
                            }
                            context.startService(serviceIntent)
                        } else {
                            // Accessibility not enabled, start manual loop fallback for the first app
                            Log.w("FreezerWidget", "Accessibility not running. Falling back to detail settings manual trigger.")
                            val firstPackage = packagesToFreeze[0]
                            val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:$firstPackage")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(settingsIntent)
                        }
                    } else {
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "❄️ All apps are already frozen and quiet.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FreezerWidget", "Error during widget quick freeze", e)
                }
            }
        }
    }
}
