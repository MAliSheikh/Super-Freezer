package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.example.data.AppFreezerRepository
import com.example.data.AppFrozenState
import com.example.data.FreezeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class FreezerAutomationService : AccessibilityService() {

    companion object {
        var isRunning = false
            private set

        const val ACTION_FORCE_STOP = "com.example.action.FORCE_STOP"
        const val ACTION_FORCE_STOP_BATCH = "com.example.action.FORCE_STOP_BATCH"
        const val EXTRA_PACKAGE_NAME = "com.example.extra.PACKAGE_NAME"
        const val EXTRA_PACKAGE_LIST = "com.example.extra.PACKAGE_LIST"
        const val EXTRA_APP_NAMES = "com.example.extra.APP_NAMES"
    }

    private val batchPackageQueue = ArrayList<String>()
    private val batchAppNameQueue = ArrayList<String>()
    private var isProcessingBatch = false
    private var currentPackage: String? = null
    private var currentAppName: String? = null
    private var lastEventTime = 0L

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingTransition: Runnable? = null
    private var pendingReturn: Runnable? = null

    // DB and Scopes for continuous background sweep
    private val repository by lazy { AppFreezerRepository(applicationContext) }
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var triggerSource: String = "APP" // "APP", "WIDGET", "BACKGROUND"
    private var lastForegroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.d("FreezerService", "Accessibility Service Connected")
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        this.serviceInfo = info

        // Start background checking loop
        startBackgroundFreezerLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val source = intent.getStringExtra("EXTRA_TRIGGER_SOURCE") ?: "APP"
            this.triggerSource = source

            when (intent.action) {
                ACTION_FORCE_STOP_BATCH -> {
                    val packages = intent.getStringArrayListExtra(EXTRA_PACKAGE_LIST)
                    val names = intent.getStringArrayListExtra(EXTRA_APP_NAMES)
                    if (packages != null && names != null) {
                        batchPackageQueue.clear()
                        batchAppNameQueue.clear()
                        batchPackageQueue.addAll(packages)
                        batchAppNameQueue.addAll(names)
                        isProcessingBatch = true
                        processNextInBatch()
                    }
                }
                ACTION_FORCE_STOP -> {
                    val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                    if (pkg != null) {
                        batchPackageQueue.clear()
                        batchAppNameQueue.clear()
                        isProcessingBatch = false
                        currentPackage = pkg
                        currentAppName = "App"
                        automateForceStop(pkg)
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun processNextInBatch() {
        if (batchPackageQueue.isNotEmpty() && batchAppNameQueue.isNotEmpty()) {
            val pkg = batchPackageQueue.removeAt(0)
            val name = batchAppNameQueue.removeAt(0)
            currentPackage = pkg
            currentAppName = name
            Log.d("FreezerService", "Processing next batch item: $pkg")
            
            // Instantly notify about freeze operation success
            NotificationHelper.showFreezeNotification(this, name, pkg)
            
            automateForceStop(pkg)
        } else {
            isProcessingBatch = false
            currentPackage = null
            currentAppName = null
            Log.d("FreezerService", "Batch force stop finished!")

            // Return to source
            if (triggerSource != "BACKGROUND") {
                scheduleReturnToSource(1000)
            }
        }
    }

    private fun scheduleNextBatchTransition(delayMs: Long = 800) {
        pendingTransition?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            if (isProcessingBatch) {
                processNextInBatch()
            }
        }
        pendingTransition = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun scheduleReturnToSource(delayMs: Long = 800) {
        pendingReturn?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            returnToSource()
        }
        pendingReturn = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun returnToSource() {
        if (triggerSource == "WIDGET") {
            try {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(homeIntent)
                Log.d("FreezerService", "Returned back to Home Screen")
            } catch (e: Exception) {
                Log.e("FreezerService", "Error returning to home", e)
            }
        } else if (triggerSource == "APP") {
            try {
                val appIntent = Intent().apply {
                    setClassName(packageName, "com.example.MainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(appIntent)
                Log.d("FreezerService", "Returned back to App")
            } catch (e: Exception) {
                Log.e("FreezerService", "Error returning to app", e)
            }
        }
    }

    private fun automateForceStop(packageName: String) {
        // Open the app details settings screen for the requested package
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        startActivity(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val newPackage = event.packageName?.toString() ?: ""

            // Close detection check for "Always Freeze" apps
            val lastPkg = lastForegroundPackage
            if (lastPkg != null && lastPkg != newPackage && lastPkg != packageName && lastPkg != "com.android.settings" && !isLauncherPackage(lastPkg)) {
                serviceScope.launch {
                    val state = repository.getAppState(lastPkg)
                    if (state != null && state.isBlacklisted && !state.isWhitelisted) {
                        Log.d("FreezerService", "Always Freeze app closed by user: $lastPkg")
                        
                        // Setup silent background freeze
                        val intent = Intent(this@FreezerAutomationService, FreezerAutomationService::class.java).apply {
                            action = ACTION_FORCE_STOP
                            putExtra(EXTRA_PACKAGE_NAME, lastPkg)
                            putExtra("EXTRA_TRIGGER_SOURCE", "BACKGROUND")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        // Mark as frozen in DB
                        val updated = state.copy(isFrozen = true)
                        repository.insertOrUpdateAppState(updated)

                        // Insert Log
                        repository.insertLog(
                            FreezeLog(
                                packageName = lastPkg,
                                appName = state.appName,
                                method = "Instant App-Close Freeze",
                                success = true
                            )
                        )

                        startService(intent)
                    }
                }
            }

            if (newPackage.isNotEmpty() && newPackage != packageName && newPackage != "com.android.settings" && !isLauncherPackage(newPackage)) {
                lastForegroundPackage = newPackage
            }

            handleAccessibilityAction()
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Throttle content change events to once every 300ms to allow smooth rendering and prevent ANRs
            if (now - lastEventTime > 300) {
                lastEventTime = now
                handleAccessibilityAction()
            }
        }
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            for (info in resolveInfos) {
                if (info.activityInfo.packageName == pkg) return true
            }
        } catch (e: Exception) {}
        return pkg == "android" || pkg.contains("launcher") || pkg.contains("trebuchet") || pkg.contains("pixel")
    }

    private fun startBackgroundFreezerLoop() {
        serviceScope.launch {
            while (isRunning) {
                try {
                    // sweep every 5 minutes in background
                    delay(5L * 60 * 1000)
                    checkAndBackgroundFreeze()
                } catch (e: Exception) {
                    Log.e("FreezerService", "Error in background sweep loop", e)
                }
            }
        }
    }

    private suspend fun checkAndBackgroundFreeze() {
        val allStates = repository.getAllAppStates()
        val packagesToFreeze = ArrayList<String>()
        val appNamesToFreeze = ArrayList<String>()
        val currentTime = System.currentTimeMillis()

        val autoFreezeDaysStr = repository.getSetting("autoFreezeDays", "7")
        val days = autoFreezeDaysStr.toIntOrNull() ?: 7
        val thresholdMs = days * 24L * 60 * 60 * 1000

        val statsMap = getRealUsageStats()

        for (state in allStates) {
            val lastUsed = statsMap[state.packageName] ?: state.lastUsedTime
            val isInactive = (currentTime - lastUsed) > thresholdMs

            val shouldFreeze = if (state.isBlacklisted && !state.isWhitelisted) {
                // Always Freeze: check in background, sweep if not currently active
                state.packageName != currentPackage && !state.isFrozen
            } else if (!state.isWhitelisted && !state.isBlacklisted) {
                isInactive && !state.isFrozen
            } else {
                false
            }

            if (shouldFreeze) {
                packagesToFreeze.add(state.packageName)
                appNamesToFreeze.add(state.appName)

                val updated = state.copy(isFrozen = true)
                repository.insertOrUpdateAppState(updated)
            }
        }

        if (packagesToFreeze.isNotEmpty()) {
            val intent = Intent(this, FreezerAutomationService::class.java).apply {
                action = ACTION_FORCE_STOP_BATCH
                putStringArrayListExtra(EXTRA_PACKAGE_LIST, packagesToFreeze)
                putStringArrayListExtra(EXTRA_APP_NAMES, appNamesToFreeze)
                putExtra("EXTRA_TRIGGER_SOURCE", "BACKGROUND")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startService(intent)

            repository.insertLog(
                FreezeLog(
                    packageName = "com.example.background",
                    appName = "Background Sweep Worker",
                    method = "Continuous Periodic Background Sweep (${packagesToFreeze.size} apps)",
                    success = true
                )
            )
        }
    }

    private fun getRealUsageStats(): Map<String, Long> {
        val statsMap = mutableMapOf<String, Long>()
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            if (usageStatsManager != null) {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - (30L * 24 * 60 * 60 * 1000L)
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
            Log.e("FreezerService", "Error querying usage stats in service sweep", e)
        }
        return statsMap
    }

    private fun handleAccessibilityAction() {
        val rootNode = rootInActiveWindow ?: return
        try {
            val pkgName = rootNode.packageName?.toString() ?: ""
            if (pkgName.contains("settings")) {
                findAndClickForceStopButtons(rootNode)
            }
        } finally {
            try {
                rootNode.recycle()
            } catch (e: Exception) {}
        }
    }

    private fun findAndClickForceStopButtons(node: AccessibilityNodeInfo) {
        // First check if Force Stop button is already disabled (meaning already frozen)
        if (isForceStopAlreadyDisabled(node)) {
            Log.d("FreezerService", "Force stop button is already disabled. Transitioning...")
            if (isProcessingBatch) {
                scheduleNextBatchTransition(600)
            } else {
                if (triggerSource != "BACKGROUND") {
                    scheduleReturnToSource(600)
                }
            }
            return
        }

        val targetTexts = setOf(
            "Force stop", "FORCE STOP", "Force Stop", "Force close", "Forcer l'arrêt", "Stop",
            "OK", "Ok", "Confirm", "CONFIRM"
        )

        val targetIds = setOf(
            "com.android.settings:id/force_stop_button",
            "com.android.settings:id/right_button",
            "android:id/button1" // Normal OK button ID in standard Android dialogs
        )

        // Depth-first search for text or visual nodes matching
        searchAndAct(node, targetTexts, targetIds)
    }

    private fun isForceStopAlreadyDisabled(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 25) return false
        val viewId = node.viewIdResourceName
        val text = node.text?.toString()?.trim()

        if (viewId == "com.android.settings:id/force_stop_button") {
            return !node.isEnabled
        }
        if (text != null && (text.equals("Force stop", true) || text.equals("Force Stop", true) || text.equals("FORCE STOP", true))) {
            return !node.isEnabled
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val isDisabled = isForceStopAlreadyDisabled(child, depth + 1)
            try {
                child.recycle()
            } catch (e: Exception) {}
            if (isDisabled) {
                return true
            }
        }
        return false
    }

    private fun searchAndAct(node: AccessibilityNodeInfo, targetTexts: Set<String>, targetIds: Set<String>, depth: Int = 0): Boolean {
        if (depth > 25) return false
        val viewId = node.viewIdResourceName
        val text = node.text?.toString()?.trim()

        if (viewId != null && targetIds.contains(viewId)) {
            if (node.isClickable && node.isEnabled) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d("FreezerService", "Clicked by ID: $viewId")
                
                // If it is standard dialog OK button, trigger batch progress
                if (isProcessingBatch && viewId == "android:id/button1") {
                    scheduleNextBatchTransition(500)
                } else if (!isProcessingBatch && viewId == "android:id/button1") {
                    if (triggerSource != "BACKGROUND") {
                        scheduleReturnToSource(600)
                    }
                }
                return true
            }
        }

        if (text != null && targetTexts.contains(text)) {
            if (node.isClickable && node.isEnabled) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d("FreezerService", "Clicked by Text: $text")

                // If this is an OK, confirm or Force Stop button and we are batching, schedule next transition:
                if (isProcessingBatch && (text.equals("OK", true) || text.equals("Ok", true) || text.equals("Confirm", true) || text.equals("CONFIRM", true))) {
                    scheduleNextBatchTransition(500)
                } else if (isProcessingBatch && (text.equals("Force stop", true) || text.equals("Force Stop", true))) {
                    scheduleNextBatchTransition(1200) // safety fallback transition
                } else if (!isProcessingBatch && (text.equals("OK", true) || text.equals("Ok", true) || text.equals("Confirm", true) || text.equals("CONFIRM", true))) {
                    if (triggerSource != "BACKGROUND") {
                        scheduleReturnToSource(600)
                    }
                }
                return true
            } else {
                // If the button itself is not clickable, check parents
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable && parent.isEnabled) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d("FreezerService", "Clicked parent of Text $text")
                        
                        if (isProcessingBatch && (text.equals("OK", true) || text.equals("Ok", true) || text.equals("Confirm", true) || text.equals("CONFIRM", true))) {
                            scheduleNextBatchTransition(500)
                        } else if (isProcessingBatch && (text.equals("Force stop", true) || text.equals("Force Stop", true))) {
                            scheduleNextBatchTransition(1200)
                        } else if (!isProcessingBatch && (text.equals("OK", true) || text.equals("Ok", true) || text.equals("Confirm", true) || text.equals("CONFIRM", true))) {
                            if (triggerSource != "BACKGROUND") {
                                scheduleReturnToSource(600)
                            }
                        }
                        try {
                            parent.recycle()
                        } catch (e: Exception) {}
                        return true
                    }
                    val oldParent = parent
                    parent = parent.parent
                    try {
                        oldParent.recycle()
                    } catch (e: Exception) {}
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val acted = searchAndAct(child, targetTexts, targetIds, depth + 1)
            try {
                child.recycle()
            } catch (e: Exception) {}
            if (acted) {
                return true
            }
        }
        return false
    }

    override fun onInterrupt() {
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
