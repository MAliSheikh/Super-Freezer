package com.example.ui

import android.content.Intent
import android.provider.Settings
import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.model.InstalledAppInfo
import com.example.viewmodel.AppFreezerViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MainFreezerScreen(viewModel: AppFreezerViewModel) {
    var activeTab by remember { mutableStateOf(0) } // 0: Apps, 1: Settings, 2: System Logs & Info
    val isDark = isSystemInDarkTheme()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = if (isDark) DeepEspresso else WarmSandNav,
                contentColor = if (isDark) WarmLinenBg else DeepEspresso,
                modifier = Modifier.navigationBarsPadding(),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Filled.Apps, contentDescription = "Apps") },
                    label = { Text("Apps", fontFamily = MaterialTheme.typography.bodyMedium.fontFamily) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (isDark) SoftPeachContainer else TerracottaPrimary,
                        indicatorColor = if (isDark) TerracottaPrimary.copy(alpha = 0.4f) else SoftPeachLight,
                        unselectedIconColor = MutedSlateBrown
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontFamily = MaterialTheme.typography.bodyMedium.fontFamily) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (isDark) SoftPeachContainer else TerracottaPrimary,
                        indicatorColor = if (isDark) TerracottaPrimary.copy(alpha = 0.4f) else SoftPeachLight,
                        unselectedIconColor = MutedSlateBrown
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Filled.Info, contentDescription = "Info & Logs") },
                    label = { Text("Logs & Info", fontFamily = MaterialTheme.typography.bodyMedium.fontFamily) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (isDark) SoftPeachContainer else TerracottaPrimary,
                        indicatorColor = if (isDark) TerracottaPrimary.copy(alpha = 0.4f) else SoftPeachLight,
                        unselectedIconColor = MutedSlateBrown
                    )
                )
            }
        },
        floatingActionButton = {
            if (activeTab == 0) {
                FloatingActionButton(
                    onClick = { viewModel.triggerBatchAutoFreeze() },
                    containerColor = TerracottaPrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AcUnit,
                            contentDescription = "Trigger Quick Auto Freeze",
                            tint = Color.White
                        )
                        Text(
                            text = "Auto Freeze",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Geometric Art Wave Accent
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehindFrost()
            )

            Crossfade(targetState = activeTab, label = "TabTransition") { tab ->
                when (tab) {
                    0 -> AppsTabScreen(viewModel)
                    1 -> SettingsTabScreen(viewModel)
                    2 -> LogsAndInfoTabScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun Modifier.drawBehindFrost(): Modifier {
    val isDark = isSystemInDarkTheme()
    return this.background(
        brush = Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    DeepEspresso,
                    Color(0xFF2C2522),
                    DeepEspresso
                )
            } else {
                listOf(
                    WarmLinenBg,
                    SoftPeachLight,
                    WarmLinenBg
                )
            }
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsTabScreen(viewModel: AppFreezerViewModel) {
    val searchVal by viewModel.searchQuery.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val apps by viewModel.filteredApps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    val isWhitelistMode by viewModel.isWhitelistMode.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()
    val isDark = isSystemInDarkTheme()

    var showWarningForSystemApp by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AppFreezer",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "SYSTEM CONTROLLER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedSlateBrown,
                    letterSpacing = 1.sp
                )
            }

            IconButton(
                onClick = { viewModel.refreshInstalledApps() },
                colors = IconButtonDefaults.iconButtonColors(contentColor = TerracottaPrimary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Apps")
            }
        }

        // Quick Stats / Mode Switch Banner from geometric design
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) TerracottaPrimary.copy(alpha = 0.25f) else SoftPeachLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .border(1.dp, WarmGrayBorder, RoundedCornerShape(24.dp)),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TerracottaPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Active info",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isWhitelistMode) "Whitelist Mode" else "Blacklist Mode",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 15.sp
                        )
                        val frozenCount = apps.count { it.isFrozen }
                        Text(
                            text = "$frozenCount apps frozen / asleep",
                            color = MutedSlateBrown,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Button(
                    onClick = { viewModel.updateWhitelistMode(!isWhitelistMode) },
                    colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("SWITCH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Help section explaining Approved & Whitelist / Blacklist
        var showHelpInfo by remember { mutableStateOf(false) }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(1.dp, WarmGrayBorder, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHelpInfo = !showHelpInfo },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Help Guide",
                            tint = TerracottaPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "How do these options and filters work?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (showHelpInfo) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Guide",
                        tint = MutedSlateBrown,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (showHelpInfo) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Customize winterization for each app using the icons on the right side:",
                        fontSize = 11.sp,
                        color = MutedSlateBrown,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Filled.AcUnit,
                            contentDescription = null,
                            tint = TerracottaPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Always Freeze Mode", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Automatically put to sleep whenever you hit 'START AUTO FREEZE' or click the home widget.", fontSize = 10.sp, color = MutedSlateBrown)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = AlertWarningGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Freeze After Sometime Mode", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Intelligent sleep. Automatically frozen during freeze sweeps only if inactive for 3 minutes.", fontSize = 10.sp, color = MutedSlateBrown)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Never Freeze (Approved/Whitelist)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Completely whitelisted. Ideal for messengers, system clocks, or mail trackers to run normally.", fontSize = 10.sp, color = MutedSlateBrown)
                        }
                    }
                }
            }
        }

        // Search bar styled elegantly
        OutlinedTextField(
            value = searchVal,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WarmGrayBorder, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("Search installed apps...", color = MutedSlateBrown) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TerracottaPrimary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = TerracottaPrimary,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Filter Badges Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterTabButton(
                title = "All",
                isActive = filterType == AppFreezerViewModel.FilterType.ALL,
                onClick = { viewModel.setFilterType(AppFreezerViewModel.FilterType.ALL) }
            )
            FilterTabButton(
                title = "Frozen",
                isActive = filterType == AppFreezerViewModel.FilterType.FROZEN,
                onClick = { viewModel.setFilterType(AppFreezerViewModel.FilterType.FROZEN) }
            )
            FilterTabButton(
                title = "Approved",
                isActive = filterType == AppFreezerViewModel.FilterType.WHITELISTED,
                onClick = { viewModel.setFilterType(AppFreezerViewModel.FilterType.WHITELISTED) }
            )
            FilterTabButton(
                title = "Blacklist",
                isActive = filterType == AppFreezerViewModel.FilterType.BLACKLISTED,
                onClick = { viewModel.setFilterType(AppFreezerViewModel.FilterType.BLACKLISTED) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Accessibility Warning status Banner
        if (!isAccessibilityEnabled) {
            Card(
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(1.dp, AlertWarningGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AlertWarningGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = AlertWarningGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility Automation Off",
                            fontWeight = FontWeight.Bold,
                            color = AlertWarningGold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Enable automated click flow helper to easily bypass device limitations.",
                            color = MutedSlateBrown,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Grant Accessibility",
                        tint = TerracottaPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Freezer Hero Quick Trigger Action Banner
        Button(
            onClick = { viewModel.triggerBatchAutoFreeze() },
            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AcUnit, contentDescription = "Freeze All", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "START AUTO FREEZE SEQUENCE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TerracottaPrimary)
            }
        } else if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.AcUnit,
                        contentDescription = "Empty list",
                        modifier = Modifier.size(60.dp),
                        tint = MutedSlateBrown
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No apps match filter criteria or search query.",
                        color = MutedSlateBrown,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Try clearing search or check settings.",
                        color = MutedSlateBrown.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppItemRow(
                        app = app,
                        onFreezeToggle = { viewModel.toggleFreeze(app) },
                        onPreferenceChanged = { pref -> viewModel.setAppFreezePreference(app, pref) },
                        onWarnSystem = { showWarningForSystemApp = app.appName }
                    )
                }
            }
        }
    }

    if (showWarningForSystemApp != null) {
        AlertDialog(
            onDismissRequest = { showWarningForSystemApp = null },
            confirmButton = {
                TextButton(onClick = { showWarningForSystemApp = null }) {
                    Text("I Understand", color = TerracottaPrimary)
                }
            },
            title = { Text("System App Warning", color = AlertWarningGold) },
            text = { Text("Freezing system apps ($showWarningForSystemApp) might cause severe performance instability or unexpected reboots. Proceed with extreme caution.", color = MaterialTheme.colorScheme.onSurface) },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun FilterTabButton(title: String, isActive: Boolean, onClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isActive) {
                    if (isDark) SoftPeachContainer else TerracottaPrimary
                } else {
                    if (isDark) Color(0xFF332B27) else Color(0xFFF4ECEA)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = if (isActive) {
                if (isDark) DeepEspresso else Color.White
            } else {
                if (isDark) SoftPeachContainer else MutedSlateBrown
            },
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pm = context.packageManager
    val appIcon = produceState<Drawable?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                pm.getApplicationIcon(packageName)
            } catch (e: Exception) {
                null
            }
        }
    }

    if (appIcon.value != null) {
        val bitmap = remember(appIcon.value) {
            try {
                val drawable = appIcon.value
                if (drawable != null) {
                    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                    drawable.toBitmap(w, h).asImageBitmap()
                } else {
                    null
                }
            } catch (t: Throwable) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "App Logo",
                modifier = modifier
            )
        } else {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = "App Icon Placeholder",
                tint = TerracottaPrimary,
                modifier = modifier
            )
        }
    } else {
        Box(
            modifier = modifier
                .background(Color.Gray.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp))
        )
    }
}

@Composable
fun AppItemRow(
    app: InstalledAppInfo,
    onFreezeToggle: () -> Unit,
    onPreferenceChanged: (preferenceCode: Int) -> Unit,
    onWarnSystem: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (app.isFrozen) {
                if (isDark) Color(0xFF2C2522).copy(alpha = 0.6f) else Color(0xFFFBF8F6)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (app.isFrozen) TerracottaPrimary.copy(alpha = 0.5f) else WarmGrayBorder,
                RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Asymmetric App Icon Loading
            AppIcon(
                packageName = app.packageName,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // App details left section
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (app.isFrozen) MutedSlateBrown else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (app.isSystemApp) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AlertWarningGold.copy(alpha = 0.15f))
                                .clickable { onWarnSystem() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("SYS", color = AlertWarningGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (app.isFrozen) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TerracottaPrimary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("COLD", color = TerracottaPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    fontSize = 11.sp,
                    color = MutedSlateBrown,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3 Preference Option States
            val isAlways = !app.isWhitelisted && app.isBlacklisted
            val isSometime = !app.isWhitelisted && !app.isBlacklisted
            val isNever = app.isWhitelisted

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 1. Always Freeze Option
                IconButton(
                    onClick = { onPreferenceChanged(1) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isAlways) TerracottaPrimary.copy(alpha = 0.15f) else Color.Transparent,
                        contentColor = if (isAlways) TerracottaPrimary else MutedSlateBrown.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AcUnit,
                        contentDescription = "Always Freeze",
                        modifier = Modifier.size(17.dp)
                    )
                }

                // 2. Freeze after sometime Option (Inactivity check)
                IconButton(
                    onClick = { onPreferenceChanged(2) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isSometime) AlertWarningGold.copy(alpha = 0.15f) else Color.Transparent,
                        contentColor = if (isSometime) AlertWarningGold else MutedSlateBrown.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = "Freeze after sometime",
                        modifier = Modifier.size(17.dp)
                    )
                }

                // 3. Never Freeze (Approved Whitelist) Option
                IconButton(
                    onClick = { onPreferenceChanged(3) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isNever) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color.Transparent,
                        contentColor = if (isNever) Color(0xFF4CAF50) else MutedSlateBrown.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Never Freeze",
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Small elegant separator
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(WarmGrayBorder.copy(alpha = 0.8f))
                        .padding(horizontal = 1.dp)
                )

                // Quick Play/Stop Action for Manual Freeze control
                IconButton(
                    onClick = onFreezeToggle,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (app.isFrozen) Color(0xFF4CAF50) else TerracottaPrimary
                    ),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (app.isFrozen) Icons.Filled.PlayArrow else Icons.Filled.Stop,
                        contentDescription = if (app.isFrozen) "Melt" else "Manual Freeze",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsTabScreen(viewModel: AppFreezerViewModel) {
    val isWhitelist by viewModel.isWhitelistMode.collectAsState()
    val autoFreezeDays by viewModel.autoFreezeDays.collectAsState()
    val freezeOnScreenOff by viewModel.freezeOnScreenOff.collectAsState()
    val includeSystem by viewModel.includeSystemApps.collectAsState()
    val isUsageStatsEnabled by viewModel.isUsageStatsEnabled.collectAsState()
    val isDark = isSystemInDarkTheme()

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = "Fine-tune freeze scheduling and bypass filters.",
            color = MutedSlateBrown,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Whitelist vs Blacklist Mode switch
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WarmGrayBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Whitelist Protocol",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isWhitelist) "Allow ONLY Whitelisted apps to run dynamically." else "Freeze ONLY manually Selected Apps.",
                            color = MutedSlateBrown,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isWhitelist,
                        onCheckedChange = { viewModel.updateWhitelistMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TerracottaPrimary,
                            checkedTrackColor = SoftPeachContainer.copy(alpha = 0.5f),
                            uncheckedThumbColor = MutedSlateBrown,
                            uncheckedTrackColor = if (isDark) Color(0xFF2C2522) else WarmGrayBorder
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Auto freeze Days trigger
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WarmGrayBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Auto‑freeze idle apps after: $autoFreezeDays days",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Slider(
                    value = autoFreezeDays.toFloat(),
                    onValueChange = { viewModel.updateAutoFreezeDays(it.toInt()) },
                    valueRange = 1f..30f,
                    colors = SliderDefaults.colors(
                        thumbColor = TerracottaPrimary,
                        activeTrackColor = TerracottaPrimary,
                        inactiveTrackColor = if (isDark) Color(0xFF2C2522) else WarmGrayBorder
                    )
                )
                Text(
                    text = "Analyzes background idle times and auto‑fully‑stops them to conserve battery life and RAM.",
                    color = MutedSlateBrown,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Toggle Freeze on Screen Off
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WarmGrayBorder, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Freeze on screen off",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Auto trigger frozen limits instantly when you sleep your device.",
                        color = MutedSlateBrown,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = freezeOnScreenOff,
                    onCheckedChange = { viewModel.updateFreezeOnScreenOff(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TerracottaPrimary,
                        checkedTrackColor = SoftPeachContainer.copy(alpha = 0.5f),
                        uncheckedThumbColor = MutedSlateBrown,
                        uncheckedTrackColor = if (isDark) Color(0xFF2C2522) else WarmGrayBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Include System apps in the Freezer
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WarmGrayBorder, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Include System Apps",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Allows freezing pre‑installed system apps (warning required).",
                        color = MutedSlateBrown,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = includeSystem,
                    onCheckedChange = { viewModel.updateIncludeSystemApps(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TerracottaPrimary,
                        checkedTrackColor = SoftPeachContainer.copy(alpha = 0.5f),
                        uncheckedThumbColor = MutedSlateBrown,
                        uncheckedTrackColor = if (isDark) Color(0xFF2C2522) else WarmGrayBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Usage Stats Data Access switch/button
        if (!isUsageStatsEnabled) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF332B27) else SoftPeachLight
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // fallback logic
                        }
                    }
                    .border(1.dp, AlertWarningGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddAlert,
                        contentDescription = "Usage permissions required Indicator",
                        tint = AlertWarningGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Real Usage Detection",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap to grant access. Without this, Subzero uses realistic idle timelines.",
                            color = MutedSlateBrown,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Accessibilities settings manual shortcut
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Accessibility, contentDescription = "Accessibility settings", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ACCESSIBILITY OPTIONS MENU", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun LogsAndInfoTabScreen(viewModel: AppFreezerViewModel) {
    val logs by viewModel.freezeLogs.collectAsState()
    val isDark = isSystemInDarkTheme()
    var activeSubTab by remember { mutableStateOf(0) } // 0: Activity Log, 1: Complete Visual Guide

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        // Aesthetic Sub-Tab Row with rounded background
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = Color.Transparent,
            contentColor = TerracottaPrimary,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("Activity Log", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                icon = { Icon(Icons.Default.History, contentDescription = "Activity Log Events", modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("Complete Visual Guide", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                icon = { Icon(Icons.Default.HelpOutline, contentDescription = "Visual User Guide", modifier = Modifier.size(18.dp)) }
            )
        }

        if (activeSubTab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Recent Activity",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Observe real-time freeze-stops & protocol triggers.",
                        color = MutedSlateBrown,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = { viewModel.clearLogs() },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = TerracottaPrimary)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Clear logs")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // OEM Bypass Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) TerracottaPrimary.copy(alpha = 0.15f) else SoftPeachLight
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .border(1.dp, WarmGrayBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🛠️ How to bypass OEM Security Restrictions?",
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) SoftPeachContainer else TerracottaPrimary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "To completely stop apps from waking up, Subzero uses standard system Accessibility features to auto-navigate app configurations & execute a real Force-Stop without requiring any developer tools or system root modifications.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Text(
                text = "Activity Events:",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No logs available. Trigger a manual or auto freeze.", color = MutedSlateBrown, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs) { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, WarmGrayBorder, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = log.appName,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    )
                                    Text(text = log.packageName, color = MutedSlateBrown, fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (log.success) {
                                                        TerracottaPrimary.copy(alpha = 0.15f)
                                                    } else {
                                                        AlertWarningGold.copy(alpha = 0.15f)
                                                    }
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (log.success) "SUCCESS" else "FAILED",
                                                color = if (log.success) TerracottaPrimary else AlertWarningGold,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Via: ${log.method}",
                                            color = MutedSlateBrown,
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                val formattedTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))
                                Text(text = formattedTime, color = MutedSlateBrown, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // COMPLETE GUIDE PAGE WITH RICH GRAPHICS
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "Control Guidelines",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Master your battery and RAM with the three subzero protocols.",
                        color = MutedSlateBrown,
                        fontSize = 11.sp
                    )
                }

                // 1st Option Guide Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, WarmGrayBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AlertWarningGold.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "Always Freeze Option",
                                        tint = AlertWarningGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "1st Action: ALWAYS FREEZE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AlertWarningGold.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "IMMEDIATE CLOSURE DAEMON",
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AlertWarningGold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "How it works: Once chosen, our background monitoring supervisor tracks window state shifts via Accessibility alerts. The physical second you close or leave the target application, Subzero instantly invokes a background force-stop sequence, eliminating background leaks immediately.",
                                fontSize = 10.5.sp,
                                color = MutedSlateBrown,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // 2nd Option Guide Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, WarmGrayBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(TerracottaPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.HourglassEmpty,
                                        contentDescription = "Freeze Sometime Option",
                                        tint = TerracottaPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "2nd Action: FREEZE AFTER SOMETIME",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(TerracottaPrimary.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "INTERACTIVE INTERVAL TRIGGER",
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TerracottaPrimary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "How it works: Perfect for secondary apps. You specify the idle day duration in the Settings tab screen slider (e.g., 3 days). Subzero sweeps background apps and queries Usage Statistics. If an app remains unopened beyond your specific limit, it softly schedules a freeze-stop.",
                                fontSize = 10.5.sp,
                                color = MutedSlateBrown,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // 3rd Option Guide Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, WarmGrayBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MutedSlateBrown.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Never Freeze Option",
                                        tint = MutedSlateBrown,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "3rd Action: NEVER FREEZE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MutedSlateBrown.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "PERMANENT WHITELIST ACCESS",
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MutedSlateBrown
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "How it works: Mark system utilities or messengers (WhatsApp, Gmail, Messages) to protect them. The subzero freeze daemon completely ignores marked whitelists, preserving instant push notifications.",
                                fontSize = 10.5.sp,
                                color = MutedSlateBrown,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // 4th Option Guide Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, WarmGrayBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(TerracottaPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Dynamic Manual Toggle Guide",
                                        tint = TerracottaPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "4th Action: HYBRID LIVE POWER ACTION",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(TerracottaPrimary.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "MANUAL FREEZE AND INSTANT THAW",
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TerracottaPrimary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "What this action button does:\n\n• 🛑 STOP Icon: The app is awake and running. Clicking this instantly triggers automated access controls to Force-Stop the target app and freeze it.\n• ▶️ PLAY Icon: The app is already frozen. Clicking this instantly thaws (melts) and opens the application fully back to active life.",
                                fontSize = 10.5.sp,
                                color = MutedSlateBrown,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Subzero List Architectures Guide Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, WarmGrayBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AlertWarningGold.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = "Core Lists Guide",
                                        tint = AlertWarningGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Core Lists: BLACKLIST vs WHITELIST",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AlertWarningGold.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "SECURITY PROTOCOLS",
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AlertWarningGold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Understanding the lists:\n\n• ❄️ Blacklist (Always Freeze): Automatically puts apps here once configured. The daemon monitors window state changes and freezes them the millisecond you close/minimize them.\n• 🛡️ Whitelist (Never Freeze): Completely protects chosen messaging utilities or clocks so they remain active for instant notifications.\n• 🕒 Schedule (Freeze after sometime): Idle apps that will only be dormantized automatically when they remain completely unused past the inactivity days limit.",
                                fontSize = 10.5.sp,
                                color = MutedSlateBrown,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Interactive flow diagram
                item {
                    VisualGuideDiagram()
                }

                // Infographic flow card
                item {
                    Text(
                        text = "Automated Sequence Flowchart",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SoftPeachLight.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, WarmGrayBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            val steps = listOf(
                                "① Grant Accessibility Settings Access (Starts Supervisor)",
                                "② Label any App with your preferred option button",
                                "③ When Closed/Idle, Subzero force-stops the app & returns you seamlessly",
                                "④ Enjoy beautiful relaxed warm lighting & robust device optimization."
                            )
                            steps.forEach { step ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = "Step Indicator",
                                        tint = TerracottaPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = step,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisualGuideDiagram(modifier: Modifier = Modifier) {
    val primaryColor = TerracottaPrimary
    val secondaryColor = AlertWarningGold
    val separatorColor = WarmGrayBorder
    val onSurface = MaterialTheme.colorScheme.onSurface
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, separatorColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "❄️ Subzero State Machine Flow",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(14.dp))
            
            // Draw custom Canvas flowchart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                
                // Draw states
                // State 1: App Active (Left)
                val rect1Left = 10f
                val rect1Width = canvasWidth * 0.24f
                val rectHeight = 50f
                val rect1Top = (canvasHeight - rectHeight) / 2
                
                // Draw State 1 (Warm Active State)
                val cornerRadius = CornerRadius(8f, 8f)
                drawRoundRect(
                    color = secondaryColor.copy(alpha = 0.20f),
                    topLeft = Offset(rect1Left, rect1Top),
                    size = androidx.compose.ui.geometry.Size(rect1Width, rectHeight),
                    cornerRadius = cornerRadius
                )
                
                // State 2: Daemon (Center)
                val rect2Width = canvasWidth * 0.36f
                val rect2Left = (canvasWidth - rect2Width) / 2
                val rect2Top = rect1Top
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.20f),
                    topLeft = Offset(rect2Left, rect2Top),
                    size = androidx.compose.ui.geometry.Size(rect2Width, rectHeight),
                    cornerRadius = cornerRadius
                )
                
                // State 3: Frozen (Right)
                val rect3Width = canvasWidth * 0.24f
                val rect3Left = canvasWidth - rect3Width - 10f
                val rect3Top = rect1Top
                drawRoundRect(
                    color = separatorColor.copy(alpha = 0.40f),
                    topLeft = Offset(rect3Left, rect3Top),
                    size = androidx.compose.ui.geometry.Size(rect3Width, rectHeight),
                    cornerRadius = cornerRadius
                )
                
                // Draw Connector Arrows
                // Arrow 1 -> 2
                val arrow1Start = rect1Left + rect1Width
                val arrow1End = rect2Left
                val arrowY = canvasHeight / 2
                drawLine(
                    color = primaryColor.copy(alpha = 0.7f),
                    start = Offset(arrow1Start, arrowY),
                    end = Offset(arrow1End, arrowY),
                    strokeWidth = 4f
                )
                // Arrowhead 1 -> 2
                drawLine(
                    color = primaryColor.copy(alpha = 0.7f),
                    start = Offset(arrow1End - 8f, arrowY - 6f),
                    end = Offset(arrow1End, arrowY),
                    strokeWidth = 4f
                )
                drawLine(
                    color = primaryColor.copy(alpha = 0.7f),
                    start = Offset(arrow1End - 8f, arrowY + 6f),
                    end = Offset(arrow1End, arrowY),
                    strokeWidth = 4f
                )
                
                // Arrow 2 -> 3
                val arrow2Start = rect2Left + rect2Width
                val arrow2End = rect3Left
                drawLine(
                    color = primaryColor.copy(alpha = 0.7f),
                    start = Offset(arrow2Start, arrowY),
                    end = Offset(arrow2End, arrowY),
                    strokeWidth = 4f
                )
                // Arrowhead 2 -> 3
                drawLine(
                    color = primaryColor.copy(alpha = 0.7f),
                    start = Offset(arrow2End - 8f, arrowY - 6f),
                    end = Offset(arrow2End, arrowY),
                    strokeWidth = 4f
                )
                drawLine(
                    color = primaryColor.copy(alpha = 0.7f),
                    start = Offset(arrow2End - 8f, arrowY + 6f),
                    end = Offset(arrow2End, arrowY),
                    strokeWidth = 4f
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))

            // Text Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "App Awake\n(In RAM)",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    modifier = Modifier.width(75.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 11.sp
                )
                Text(
                    text = "Accessibility Core\n(Silent Stop Intent)",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 11.sp
                )
                Text(
                    text = "Clean Freeze\n(Dormant)",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    modifier = Modifier.width(75.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 11.sp
                )
            }
        }
    }
}
