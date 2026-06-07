package com.arcadesoftware.lykonshield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arcadesoftware.lykonshield.ui.theme.LykonShieldTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.arcadesoftware.lykon.AdblockEngine
import android.net.VpnService
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_LykonShield)
        super.onCreate(savedInstanceState)
        
        // Initialize adblocker engine and stats manager
        AdblockEngine.init(applicationContext)
        ShieldStatsManager.init(applicationContext)
        
        val prefs = getSharedPreferences("lykon_shield_prefs", MODE_PRIVATE)
        enableEdgeToEdge()
        setContent {
            val initialThemeMode = remember { prefs.getInt("theme_mode", 0) }
            val initialLiquidGlass = remember { prefs.getBoolean("liquid_glass_enabled", true) }
            val initialAdvancedStats = remember { prefs.getBoolean("advanced_network_stats", false) }
            val initialProtection = remember { prefs.getBoolean("protection_enabled", false) }
            val initialExcludedApps = remember { prefs.getStringSet("excluded_apps", emptySet()) ?: emptySet() }
            val initialBackdropBlurRadius = remember { prefs.getFloat("backdrop_blur_radius", 24f) }
            val initialCustomShaders = remember { prefs.getBoolean("custom_shaders_enabled", true) }
            val initialHighlightCaptures = remember { prefs.getBoolean("highlight_captures_enabled", false) }
            val initialForceSystemBlur = remember { prefs.getBoolean("force_system_blur_enabled", true) }
            val initialRenderCacheSize = remember { prefs.getInt("render_cache_size", 512) }
            val initialWidgetPromptShown = remember { prefs.getBoolean("widget_prompt_shown_v4", false) }

            var requiresHardUpdate by remember { mutableStateOf(false) }
            var requiresSoftUpdate by remember { mutableStateOf(false) }
            var showSoftUpdateDialog by remember { mutableStateOf(false) }

            var themeMode by remember { mutableIntStateOf(initialThemeMode) }
            var showThemeDialog by rememberSaveable { mutableStateOf(false) }
            var showWidgetPrompt by rememberSaveable { mutableStateOf(!initialWidgetPromptShown) }
            var isLiquidGlassEnabled by remember { mutableStateOf(initialLiquidGlass) }
            var isAdvancedNetworkStatsEnabled by remember { mutableStateOf(initialAdvancedStats) }
            var backdropBlurRadius by remember { mutableStateOf(initialBackdropBlurRadius) }
            var isCustomShadersEnabled by remember { mutableStateOf(initialCustomShaders) }
            var isHighlightCapturesEnabled by remember { mutableStateOf(initialHighlightCaptures) }
            var isForceSystemBlurEnabled by remember { mutableStateOf(initialForceSystemBlur) }
            var renderCacheSize by remember { mutableStateOf(initialRenderCacheSize) }

            LaunchedEffect(Unit) {
                val currentVersionCode = BuildConfig.VERSION_CODE
                AppConfigManager.checkRemoteConfig(this@MainActivity, currentVersionCode)
                requiresHardUpdate = AppConfigManager.requiresHardUpdate
                requiresSoftUpdate = AppConfigManager.requiresSoftUpdate
                if (requiresSoftUpdate) {
                    showSoftUpdateDialog = true
                }
                
                if (requiresHardUpdate) {
                    // Turn off protection if hard update is required
                    prefs.edit().putBoolean("protection_enabled", false).apply()
                    if (LykonVpnService.isVpnActive) {
                        val intent = Intent(this@MainActivity, LykonVpnService::class.java).apply {
                            action = LykonVpnService.ACTION_STOP
                        }
                        this@MainActivity.startService(intent)
                    }
                }
            }

            LaunchedEffect(themeMode) {
                prefs.edit().putInt("theme_mode", themeMode).apply()
            }
            LaunchedEffect(isLiquidGlassEnabled) {
                prefs.edit().putBoolean("liquid_glass_enabled", isLiquidGlassEnabled).apply()
            }
            LaunchedEffect(isAdvancedNetworkStatsEnabled) {
                prefs.edit().putBoolean("advanced_network_stats", isAdvancedNetworkStatsEnabled).apply()
            }
            LaunchedEffect(backdropBlurRadius) {
                prefs.edit().putFloat("backdrop_blur_radius", backdropBlurRadius).apply()
            }
            LaunchedEffect(isCustomShadersEnabled) {
                prefs.edit().putBoolean("custom_shaders_enabled", isCustomShadersEnabled).apply()
            }
            LaunchedEffect(isHighlightCapturesEnabled) {
                prefs.edit().putBoolean("highlight_captures_enabled", isHighlightCapturesEnabled).apply()
            }
            LaunchedEffect(isForceSystemBlurEnabled) {
                prefs.edit().putBoolean("force_system_blur_enabled", isForceSystemBlurEnabled).apply()
            }
            LaunchedEffect(renderCacheSize) {
                prefs.edit().putInt("render_cache_size", renderCacheSize).apply()
            }

            LaunchedEffect(showWidgetPrompt) {
                if (!showWidgetPrompt && !initialWidgetPromptShown) {
                    prefs.edit().putBoolean("widget_prompt_shown_v4", true).apply()
                }
            }

            val appWidgetManager = remember { android.appwidget.AppWidgetManager.getInstance(applicationContext) }
            val myProvider = remember { android.content.ComponentName(applicationContext, ShieldWidgetProvider::class.java) }

            val isDark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val view = androidx.compose.ui.platform.LocalView.current
            if (!view.isInEditMode) {
                androidx.compose.runtime.SideEffect {
                    val window = (view.context as android.app.Activity).window
                    androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                    androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
                }
            }
            LykonShieldTheme(darkTheme = isDark, dynamicColor = false) {
                val isLightTheme = !isDark
                CompositionLocalProvider(
                    LocalIsLightTheme provides isLightTheme,
                    LocalIsLiquidGlassEnabled provides isLiquidGlassEnabled,
                    LocalBackdropBlurRadius provides backdropBlurRadius,
                    LocalIsCustomShadersEnabled provides isCustomShadersEnabled
                ) {
                    val backdrop = rememberLayerBackdrop {
                        drawRect(if (isLightTheme) Color(0xFFF2F2F7) else Color.Black)
                        drawContent()
                    }
                    val backgroundBackdrop = rememberLayerBackdrop {
                        drawRect(if (isLightTheme) Color(0xFFF2F2F7) else Color.Black)
                        drawContent()
                    }
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: "home"

                val tabs = remember { listOf("home", "blocked", "settings") }
                var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

                LaunchedEffect(currentRoute) {
                    val idx = tabs.indexOf(currentRoute)
                    if (idx >= 0 && idx != selectedTabIndex) {
                        selectedTabIndex = idx
                    }
                }

                LaunchedEffect(selectedTabIndex) {
                    val route = tabs[selectedTabIndex]
                    if (route != currentRoute && route in listOf("home", "blocked", "settings")) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                val contentColor = if (isLightTheme) Color.Black else Color.White

                var isProtectionEnabled by remember { mutableStateOf(initialProtection) }
                var excludedApps by remember { mutableStateOf(initialExcludedApps) }

                // Load and save protection level state
                val initialProtectionLevelName = remember { prefs.getString("protection_level", "TRACKER_AND_ADS") ?: "TRACKER_AND_ADS" }
                val initialProtectionLevel = remember {
                    try {
                        ProtectionLevel.valueOf(initialProtectionLevelName)
                    } catch (e: Exception) {
                        ProtectionLevel.TRACKER_AND_ADS
                    }
                }
                var protectionLevel by remember { mutableStateOf(initialProtectionLevel) }
                val context = androidx.compose.ui.platform.LocalContext.current

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {}

                LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val permissionState = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.POST_NOTIFICATIONS
                        )
                        if (permissionState != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                LaunchedEffect(protectionLevel) {
                    val wasChanged = prefs.getString("protection_level", "") != protectionLevel.name
                    prefs.edit().putString("protection_level", protectionLevel.name).apply()
                    if (wasChanged) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            AdblockEngine.reloadFilters(context)
                        }
                    }
                }

                val vpnLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                        val startIntent = Intent(context, LykonVpnService::class.java).apply {
                            action = LykonVpnService.ACTION_START
                        }
                        context.startService(startIntent)
                        isProtectionEnabled = true
                    } else {
                        isProtectionEnabled = false
                    }
                }

                val toggleProtection = {
                    if (isProtectionEnabled) {
                        val stopIntent = Intent(context, LykonVpnService::class.java).apply {
                            action = LykonVpnService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                        isProtectionEnabled = false
                    } else {
                        val prepareIntent = VpnService.prepare(context)
                        if (prepareIntent != null) {
                            vpnLauncher.launch(prepareIntent)
                        } else {
                            val startIntent = Intent(context, LykonVpnService::class.java).apply {
                                action = LykonVpnService.ACTION_START
                            }
                            context.startService(startIntent)
                            isProtectionEnabled = true
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    if (initialProtection) {
                        val prepareIntent = VpnService.prepare(context)
                        if (prepareIntent == null) {
                            val startIntent = Intent(context, LykonVpnService::class.java).apply {
                                action = LykonVpnService.ACTION_START
                            }
                            context.startService(startIntent)
                        } else {
                            isProtectionEnabled = false
                        }
                    }
                }

                LaunchedEffect(isProtectionEnabled) {
                    prefs.edit().putBoolean("protection_enabled", isProtectionEnabled).apply()
                }
                LaunchedEffect(excludedApps) {
                    prefs.edit().putStringSet("excluded_apps", excludedApps).apply()
                }

                val homeState = rememberLazyListState()
                val blockedState = rememberLazyListState()
                val settingsState = rememberLazyListState()

                val homeScrollOffset = rememberLazyListScrollOffset(homeState)
                val blockedScrollOffset = rememberLazyListScrollOffset(blockedState)
                val settingsScrollOffset = rememberLazyListScrollOffset(settingsState)

                val scrollOffsetProvider = remember(currentRoute) {
                    {
                        when (currentRoute) {
                            "home" -> homeScrollOffset
                            "blocked" -> blockedScrollOffset
                            "settings" -> settingsScrollOffset
                            else -> 0f
                        }
                    }
                }

                val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                val showBars = currentRoute in listOf("home", "blocked", "settings")
                val dialogBackdrop = rememberLayerBackdrop()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isLightTheme) Color(0xFFF2F2F7) else Color.Black)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backgroundBackdrop)
                    )
                    Box(
                        modifier = Modifier
                            .layerBackdrop(backdrop)
                            .layerBackdrop(dialogBackdrop)
                            .fillMaxSize()
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable("home") {
                                HomeScreen(
                                    state = homeState,
                                    isProtectionEnabled = isProtectionEnabled,
                                    onProtectionToggle = toggleProtection,
                                    onExcludeAppsClick = { navController.navigate("exclude_apps") },
                                    topPadding = 12.dp + statusBarPadding,
                                    bottomPadding = 88.dp + navBarPadding,
                                    backdrop = backgroundBackdrop,
                                    protectionLevel = protectionLevel,
                                    onProtectionLevelChange = { protectionLevel = it }
                                )
                            }
                            composable("blocked") {
                                BlockedScreen(
                                    state = blockedState,
                                    isProtectionEnabled = isProtectionEnabled,
                                    isAdvancedNetworkStatsEnabled = isAdvancedNetworkStatsEnabled,
                                    topPadding = 12.dp + statusBarPadding,
                                    bottomPadding = 88.dp + navBarPadding,
                                    backdrop = backgroundBackdrop
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    state = settingsState,
                                    themeMode = themeMode,
                                    onThemeClick = { showThemeDialog = true },
                                    onExcludeAppsClick = { navController.navigate("exclude_apps") },
                                    onDeveloperClick = { navController.navigate("developer") },
                                    onFaqClick = { navController.navigate("faq") },
                                    topPadding = 12.dp + statusBarPadding,
                                    bottomPadding = 88.dp + navBarPadding,
                                    backdrop = backgroundBackdrop
                                )
                            }
                            composable("developer") {
                                DeveloperScreen(
                                    navController = navController,
                                    isAdvancedNetworkStatsEnabled = isAdvancedNetworkStatsEnabled,
                                    onAdvancedNetworkStatsToggle = { isAdvancedNetworkStatsEnabled = it },
                                    isLiquidGlassEnabled = isLiquidGlassEnabled,
                                    onLiquidGlassToggle = { isLiquidGlassEnabled = it },
                                    isCustomShadersEnabled = isCustomShadersEnabled,
                                    onCustomShadersToggle = { isCustomShadersEnabled = it },
                                    backdropBlurRadius = backdropBlurRadius,
                                    onBackdropBlurRadiusChange = { backdropBlurRadius = it },
                                    isHighlightCapturesEnabled = isHighlightCapturesEnabled,
                                    onHighlightCapturesToggle = { isHighlightCapturesEnabled = it },
                                    isForceSystemBlurEnabled = isForceSystemBlurEnabled,
                                    onForceSystemBlurToggle = { isForceSystemBlurEnabled = it },
                                    renderCacheSize = renderCacheSize,
                                    onRenderCacheSizeChange = { renderCacheSize = it },
                                    topPadding = 16.dp + statusBarPadding,
                                    bottomPadding = 16.dp + navBarPadding,
                                    backdrop = backgroundBackdrop
                                )
                            }
                            composable("exclude_apps") {
                                ExcludeAppsScreen(
                                    navController = navController,
                                    excludedApps = excludedApps,
                                    onToggleApp = { pkg ->
                                        excludedApps = if (excludedApps.contains(pkg)) {
                                            excludedApps - pkg
                                         } else {
                                            excludedApps + pkg
                                         }
                                     },
                                     topPadding = 16.dp + statusBarPadding,
                                     bottomPadding = 16.dp + navBarPadding,
                                     backdrop = backgroundBackdrop
                                 )
                             }
                            composable("faq") {
                                FaqScreen(
                                    navController = navController,
                                    topPadding = 16.dp + statusBarPadding,
                                    bottomPadding = 16.dp + navBarPadding,
                                    backdrop = backgroundBackdrop
                                )
                            }
                        }
                    }

                    if (showBars) {
                        val topBarTitle = when (currentRoute) {
                            "home" -> "Shield"
                            "blocked" -> "Blocked"
                            "settings" -> "Settings"
                            else -> "Shield"
                        }
//                        CollapsibleTopBar(
//                            title = topBarTitle,
//                            scrollOffsetProvider = scrollOffsetProvider,
//                            backdrop = backdrop,
//                            modifier = Modifier.align(Alignment.TopCenter)
//                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .width(280.dp)
                                .navigationBarsPadding()
                                .padding(bottom = 24.dp)
                        ) {
                            LiquidBottomTabs(
                                selectedTabIndex = { selectedTabIndex },
                                onTabSelected = { selectedTabIndex = it },
                                backdrop = backdrop,
                                tabsCount = 3,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LiquidBottomTab(onClick = { selectedTabIndex = 0 }) {
                                    val isSelected = selectedTabIndex == 0
                                    val iconColor = if (isSelected) (if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF)) else Color(0xFF8E8E93)
                                    Icon(
                                        imageVector = if (isSelected) SFHouseFilledIcon else SFHouseIcon,
                                        contentDescription = "Home",
                                        tint = iconColor
                                    )
                                    Text(
                                        text = "Home",
                                        color = iconColor,
                                        fontSize = 11.sp
                                    )
                                }
                                LiquidBottomTab(onClick = { selectedTabIndex = 1 }) {
                                    val isSelected = selectedTabIndex == 1
                                    val iconColor = if (isSelected) (if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF)) else Color(0xFF8E8E93)
                                    Icon(
                                        imageVector = if (isSelected) ShieldFilledIcon else ShieldIcon,
                                        contentDescription = "Blocked",
                                        tint = iconColor
                                    )
                                    Text(
                                        text = "Blocked",
                                        color = iconColor,
                                        fontSize = 11.sp
                                    )
                                }
                                LiquidBottomTab(onClick = { selectedTabIndex = 2 }) {
                                    val isSelected = selectedTabIndex == 2
                                    val iconColor = if (isSelected) (if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF)) else Color(0xFF8E8E93)
                                    Icon(
                                        imageVector = if (isSelected) SFGearshapeFilledIcon else SFGearshapeIcon,
                                        contentDescription = "Settings",
                                        tint = iconColor
                                    )
                                    Text(
                                        text = "Settings",
                                        color = iconColor,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    if (showThemeDialog) {
                        androidx.activity.compose.BackHandler {
                            showThemeDialog = false
                        }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .layerBackdrop(dialogBackdrop)
                                    .background(Color.Black.copy(alpha = if (isLightTheme) 0.08f else 0.3f))
                                    .clickable(
                                        interactionSource = null,
                                        indication = null,
                                        onClick = { showThemeDialog = false }
                                    )
                            )
                            IosThemeDialog(
                                currentTheme = themeMode,
                                onThemeSelect = { themeMode = it },
                                onDismiss = { showThemeDialog = false },
                                backdrop = rememberCombinedBackdrop(backdrop, dialogBackdrop)
                            )
                        }
                    }

                    if (showWidgetPrompt) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .clickable(enabled = false) {}
                            ) {
                                IosWidgetPromptDialog(
                                    onDismiss = { showWidgetPrompt = false },
                                    onAddWidget = {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                                appWidgetManager.requestPinAppWidget(myProvider, null, null)
                                            } else {
                                                android.widget.Toast.makeText(applicationContext, "Please add the widget from your launcher", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            android.widget.Toast.makeText(applicationContext, "Please add the widget from your launcher", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    backdrop = rememberCombinedBackdrop(backdrop, dialogBackdrop)
                                )
                            }
                        }
                    }

                    if (requiresHardUpdate) {
                        val activity = (androidx.compose.ui.platform.LocalContext.current as? android.app.Activity)
                        UpdateBlockDialog(
                            updateUrl = AppConfigManager.updateUrl,
                            updateMessage = AppConfigManager.updateMessage,
                            isHardUpdate = true,
                            onDismiss = { activity?.finishAffinity() },
                            backdrop = backdrop
                        )
                    } else if (showSoftUpdateDialog) {
                        UpdateBlockDialog(
                            updateUrl = AppConfigManager.updateUrl,
                            updateMessage = AppConfigManager.updateMessage,
                            isHardUpdate = false,
                            onDismiss = { showSoftUpdateDialog = false },
                            backdrop = backdrop
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
fun rememberLazyListScrollOffset(state: LazyListState): Float {
    return remember(state) {
        derivedStateOf {
            if (state.layoutInfo.visibleItemsInfo.isEmpty()) {
                0f
            } else {
                val firstItem = state.layoutInfo.visibleItemsInfo.first()
                if (firstItem.index > 0) {
                    1000f
                } else {
                    -firstItem.offset.toFloat()
                }
            }
        }
    }.value
}

// ─── iOS SF Symbols-style Icons ───────────────────────────────────────────────

// SF Symbol: house (outlined)
val SFHouseIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SFHouse",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Roof
        moveTo(3f, 10.5f)
        lineTo(12f, 3f)
        lineTo(21f, 10.5f)
        // House body
        moveTo(5f, 9.5f)
        verticalLineTo(19f)
        curveTo(5f, 19.55f, 5.45f, 20f, 6f, 20f)
        horizontalLineTo(9.5f)
        verticalLineTo(14.5f)
        curveTo(9.5f, 14.22f, 9.72f, 14f, 10f, 14f)
        horizontalLineTo(14f)
        curveTo(14.28f, 14f, 14.5f, 14.22f, 14.5f, 14.5f)
        verticalLineTo(20f)
        horizontalLineTo(18f)
        curveTo(18.55f, 20f, 19f, 19.55f, 19f, 19f)
        verticalLineTo(9.5f)
    }.build()

// SF Symbol: house.fill
val SFHouseFilledIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SFHouseFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Filled house body
        path(
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
        ) {
            moveTo(5f, 10f)
            verticalLineTo(19f)
            curveTo(5f, 19.55f, 5.45f, 20f, 6f, 20f)
            horizontalLineTo(9.5f)
            verticalLineTo(14.5f)
            curveTo(9.5f, 13.95f, 9.95f, 13.5f, 10.5f, 13.5f)
            horizontalLineTo(13.5f)
            curveTo(14.05f, 13.5f, 14.5f, 13.95f, 14.5f, 14.5f)
            verticalLineTo(20f)
            horizontalLineTo(18f)
            curveTo(18.55f, 20f, 19f, 19.55f, 19f, 19f)
            verticalLineTo(10f)
            lineTo(12f, 4f)
            close()
        }
        // Roof
        path(
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
        ) {
            moveTo(12f, 2.5f)
            lineTo(2.5f, 10.5f)
            curveTo(2.2f, 10.75f, 2.25f, 11.2f, 2.6f, 11.4f)
            curveTo(2.95f, 11.6f, 3.4f, 11.5f, 3.65f, 11.2f)
            lineTo(12f, 4.2f)
            lineTo(20.35f, 11.2f)
            curveTo(20.6f, 11.5f, 21.05f, 11.6f, 21.4f, 11.4f)
            curveTo(21.75f, 11.2f, 21.8f, 10.75f, 21.5f, 10.5f)
            close()
        }
    }.build()

// SF Symbol: shield (outlined)
val ShieldIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SFShield",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(12f, 2.5f)
        curveTo(12f, 2.5f, 5f, 4.5f, 4f, 5.5f)
        curveTo(4f, 5.5f, 3.5f, 11f, 4.5f, 14f)
        curveTo(5.5f, 17f, 8f, 19.5f, 12f, 21.5f)
        curveTo(16f, 19.5f, 18.5f, 17f, 19.5f, 14f)
        curveTo(20.5f, 11f, 20f, 5.5f, 20f, 5.5f)
        curveTo(19f, 4.5f, 12f, 2.5f, 12f, 2.5f)
        close()
    }.build()

// SF Symbol: shield.fill
val ShieldFilledIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SFShieldFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
    ) {
        moveTo(12f, 2.5f)
        curveTo(12f, 2.5f, 5f, 4.5f, 4f, 5.5f)
        curveTo(4f, 5.5f, 3.5f, 11f, 4.5f, 14f)
        curveTo(5.5f, 17f, 8f, 19.5f, 12f, 21.5f)
        curveTo(16f, 19.5f, 18.5f, 17f, 19.5f, 14f)
        curveTo(20.5f, 11f, 20f, 5.5f, 20f, 5.5f)
        curveTo(19f, 4.5f, 12f, 2.5f, 12f, 2.5f)
        close()
    }.build()

// SF Symbol: gearshape (outlined)
val SFGearshapeIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SFGearshape",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Gear teeth outline
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        ) {
            // Outer gear path with 8 teeth — iOS gearshape style (wider, rounded teeth)
            moveTo(12f, 1.5f)
            lineTo(13.4f, 1.6f)
            lineTo(14f, 3.6f)
            lineTo(15.8f, 4.3f)
            lineTo(17.6f, 3.2f)
            lineTo(18.7f, 4f)
            lineTo(19.8f, 4.8f) // top-right adjustment
            lineTo(18.9f, 6.8f) // was 19.2, 6.8 — pulled in for better shape
            lineTo(19.4f, 8.6f)
            lineTo(21.5f, 9f)
            lineTo(21.8f, 10.4f) // top of right tooth
            lineTo(22.0f, 12f) // center right
            lineTo(21.8f, 13.6f)
            lineTo(21.5f, 15f)
            lineTo(19.4f, 15.4f)
            lineTo(18.9f, 17.2f)
            lineTo(19.8f, 19.2f) // was 20, 19 — adjusted
            lineTo(18.7f, 20f)
            lineTo(17.6f, 20.8f)
            lineTo(15.8f, 19.7f)
            lineTo(14f, 20.4f)
            lineTo(13.4f, 22.4f)
            lineTo(12f, 22.5f)
            lineTo(10.6f, 22.4f)
            lineTo(10f, 20.4f)
            lineTo(8.2f, 19.7f)
            lineTo(6.4f, 20.8f)
            lineTo(5.3f, 20f)
            lineTo(4.2f, 19.2f)
            lineTo(5.1f, 17.2f)
            lineTo(4.6f, 15.4f)
            lineTo(2.5f, 15f)
            lineTo(2.2f, 13.6f)
            lineTo(2f, 12f)
            lineTo(2.2f, 10.4f)
            lineTo(2.5f, 9f)
            lineTo(4.6f, 8.6f)
            lineTo(5.1f, 6.8f)
            lineTo(4.2f, 4.8f)
            lineTo(5.3f, 4f)
            lineTo(6.4f, 3.2f)
            lineTo(8.2f, 4.3f)
            lineTo(10f, 3.6f)
            lineTo(10.6f, 1.6f)
            close()
        }
        // Center circle
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 1.5f
        ) {
            // Circle at center radius ~3.5
            moveTo(15.5f, 12f)
            curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12f, 15.5f)
            curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12f)
            curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12f, 8.5f)
            curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12f)
            close()
        }
    }.build()

// SF Symbol: gearshape.fill
val SFGearshapeFilledIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SFGearshapeFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Single path with EvenOdd fill: outer gear + inner circle = transparent center hole
        path(
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        ) {
            // Outer gear body
            moveTo(12f, 1.5f)
            lineTo(13.4f, 1.6f)
            lineTo(14f, 3.6f)
            lineTo(15.8f, 4.3f)
            lineTo(17.6f, 3.2f)
            lineTo(18.7f, 4f)
            lineTo(19.8f, 4.8f)
            lineTo(18.9f, 6.8f)
            lineTo(19.4f, 8.6f)
            lineTo(21.5f, 9f)
            lineTo(21.8f, 10.4f)
            lineTo(22.0f, 12f)
            lineTo(21.8f, 13.6f)
            lineTo(21.5f, 15f)
            lineTo(19.4f, 15.4f)
            lineTo(18.9f, 17.2f)
            lineTo(19.8f, 19.2f)
            lineTo(18.7f, 20f)
            lineTo(17.6f, 20.8f)
            lineTo(15.8f, 19.7f)
            lineTo(14f, 20.4f)
            lineTo(13.4f, 22.4f)
            lineTo(12f, 22.5f)
            lineTo(10.6f, 22.4f)
            lineTo(10f, 20.4f)
            lineTo(8.2f, 19.7f)
            lineTo(6.4f, 20.8f)
            lineTo(5.3f, 20f)
            lineTo(4.2f, 19.2f)
            lineTo(5.1f, 17.2f)
            lineTo(4.6f, 15.4f)
            lineTo(2.5f, 15f)
            lineTo(2.2f, 13.6f)
            lineTo(2f, 12f)
            lineTo(2.2f, 10.4f)
            lineTo(2.5f, 9f)
            lineTo(4.6f, 8.6f)
            lineTo(5.1f, 6.8f)
            lineTo(4.2f, 4.8f)
            lineTo(5.3f, 4f)
            lineTo(6.4f, 3.2f)
            lineTo(8.2f, 4.3f)
            lineTo(10f, 3.6f)
            lineTo(10.6f, 1.6f)
            close()
            // Inner circle cutout (EvenOdd makes this a transparent hole)
            moveTo(15.5f, 12f)
            curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12f, 15.5f)
            curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12f)
            curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12f, 8.5f)
            curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12f)
            close()
        }
    }.build()