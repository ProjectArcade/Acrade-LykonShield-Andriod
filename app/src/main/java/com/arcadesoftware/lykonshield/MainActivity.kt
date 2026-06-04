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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by rememberSaveable { mutableIntStateOf(0) }
            var showThemeDialog by rememberSaveable { mutableStateOf(false) }
            val isDark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            LykonShieldTheme(darkTheme = isDark, dynamicColor = false) {
                val isLightTheme = !isDark
                CompositionLocalProvider(LocalIsLightTheme provides isLightTheme) {
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

                var isProtectionEnabled by rememberSaveable { mutableStateOf(false) }
                var excludedApps by rememberSaveable { mutableStateOf(setOf<String>()) }

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
                                    onProtectionToggle = { isProtectionEnabled = !isProtectionEnabled },
                                    onExcludeAppsClick = { navController.navigate("exclude_apps") },
                                    topPadding = 56.dp + statusBarPadding,
                                    bottomPadding = 88.dp + navBarPadding,
                                    backdrop = backgroundBackdrop
                                )
                            }
                            composable("blocked") {
                                BlockedScreen(
                                    state = blockedState,
                                    isProtectionEnabled = isProtectionEnabled,
                                    topPadding = 56.dp + statusBarPadding,
                                    bottomPadding = 88.dp + navBarPadding
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    state = settingsState,
                                    themeMode = themeMode,
                                    onThemeClick = { showThemeDialog = true },
//                                    onDeveloperClick = { navController.navigate("developer") },
                                    onExcludeAppsClick = { navController.navigate("exclude_apps") },
                                    topPadding = 56.dp + statusBarPadding,
                                    bottomPadding = 88.dp + navBarPadding,
                                    backdrop = backgroundBackdrop
                                )
                            }
                            composable("developer") {
                                DeveloperScreen(
                                    navController = navController,
                                    topPadding = 16.dp + statusBarPadding,
                                    bottomPadding = 16.dp + navBarPadding,
                                    backdrop = backdrop
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
                                    backdrop = backdrop
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
                        CollapsibleTopBar(
                            title = topBarTitle,
                            scrollOffsetProvider = scrollOffsetProvider,
                            backdrop = backdrop,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )

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
                                        imageVector = if (isSelected) Icons.Filled.Home else Icons.Outlined.Home,
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
                                        imageVector = if (isSelected) Icons.Filled.Lock else Icons.Outlined.Lock,
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
                                        imageVector = if (isSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
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
                        val dialogBackdrop = rememberLayerBackdrop()
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .layerBackdrop(dialogBackdrop)
                                    .background(Color.Transparent)
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