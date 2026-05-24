package com.example.model

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isFrozen: Boolean = false,
    val isWhitelisted: Boolean = false,
    val isBlacklisted: Boolean = false,
    val lastUsedTime: Long = 0L,
    val iconResId: Int = 0, // local fallback or loading identifier
    val isLaunchable: Boolean = false
)
