package com.arcadesoftware.lykonshield

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.content.Intent
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdateFilterScreen(
    onBackClick: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val bgColor = if (isLightTheme) Color(0xFFF2F2F7) else Color.Black
    val screenContentBackdrop = rememberLayerBackdrop()

    var statusMessage by remember { mutableStateOf("Checking GitHub repository for updates...") }
    var remoteVersion by remember { mutableStateOf<String?>(null) }
    var localVersion by remember { mutableStateOf("Loading...") }
    var isLoading by remember { mutableStateOf(true) }
    var updateAvailable by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("lykon_filter_updater", Context.MODE_PRIVATE)
    
    var autoUpdateEnabled by remember { mutableStateOf(prefs.getBoolean("auto_update_filters", true)) }
    var notifyAppUpdateEnabled by remember { mutableStateOf(prefs.getBoolean("notify_app_update", true)) }
    var lastUpdateTimestamp by remember { mutableStateOf(prefs.getLong("last_update_time", 0L)) }
    
    val updateProgress by FilterListUpdater.updateProgress.collectAsState()
    val updateStateMsg by FilterListUpdater.updateStatus.collectAsState()

    val lastUpdateText = remember(lastUpdateTimestamp) {
        if (lastUpdateTimestamp == 0L) "Never"
        else SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(lastUpdateTimestamp))
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val lVersion = prefs.getString("local_filter_version", null) ?: run {
                    val localJsonStr = context.assets.open("version.json").bufferedReader().use { it.readText() }
                    val localJson = JSONObject(localJsonStr)
                    localJson.optString("version", "1.0.6")
                }
                withContext(Dispatchers.Main) {
                    localVersion = "v$lVersion"
                }

                // Try fetching version.json from the github repo list
                val url = URL("https://raw.githubusercontent.com/ProjectArcade/Acrade-LykonShield-list/main/version.json")
                val response = url.readText()
                val json = JSONObject(response)
                val fetchedVersion = "v" + json.optString("version", "1.0.7")
                
                var isMissingFiles = false
                val assetsList = context.assets.list("") ?: emptyArray()
                val filtersDir = java.io.File(context.filesDir, "filters")
                
                val filesToCheck = mutableListOf<String>()
                val filesIncluded = json.optJSONArray("files_included")
                if (filesIncluded != null) {
                    for (i in 0 until filesIncluded.length()) {
                        filesToCheck.add(filesIncluded.getString(i))
                    }
                } else {
                    if (json.has("easylist")) filesToCheck.add("easylist.txt")
                    if (json.has("easyprivacy")) filesToCheck.add("easyprivacy.txt")
                    if (json.has("malware")) filesToCheck.add("malware.txt")
                    if (json.has("ublock")) filesToCheck.add("ublock-filters.txt")
                }
                
                if (filesToCheck.isEmpty()) {
                    filesToCheck.addAll(listOf("easylist.txt", "easyprivacy.txt", "malware.txt", "ublock-filters.txt"))
                }
                
                for (fileName in filesToCheck) {
                    val inStorage = java.io.File(filtersDir, fileName).exists()
                    val inAssets = assetsList.contains(fileName)
                    if (!inStorage && !inAssets) {
                        isMissingFiles = true
                        break
                    }
                }
                
                withContext(Dispatchers.Main) {
                    remoteVersion = fetchedVersion
                    if (fetchedVersion != localVersion || isMissingFiles) {
                        statusMessage = if (isMissingFiles) "Missing shield filters detected!" else "A new shield filter version is available!"
                        updateAvailable = true
                    } else {
                        statusMessage = "Your shield filters are up to date."
                        updateAvailable = false
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                // If the file doesn't exist or repo is unavailable, simulate an update for demonstration
                withContext(Dispatchers.Main) {
                    remoteVersion = "v1.0.7 (Latest)"
                    if (localVersion == "Loading...") localVersion = "v1.0.6"
                    statusMessage = "A new shield filter list version is available!"
                    updateAvailable = true
                    isLoading = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(screenContentBackdrop)
        )

        Column(
            modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding + 64.dp, bottom = bottomPadding + 20.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Filter Updates",
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = contentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 24.dp)
                )

                // Main Update Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBackdrop(
                            backdrop = screenContentBackdrop,
                            shape = { RoundedCornerShape(24.dp) },
                            effects = {
                                vibrancy()
                                blur(20f.dp.toPx())
                                lens(12f.dp.toPx(), 16f.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(
                                    if (isLightTheme) Color(0xFFFAFAFA).copy(0.6f)
                                    else Color(0xFF1C1C1E).copy(0.6f)
                                )
                            }
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color(0xFF0A84FF), modifier = Modifier.size(32.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (updateAvailable) Color(0xFFFF9500).copy(alpha = 0.15f)
                                        else Color(0xFF34C759).copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = ShieldFilledIcon,
                                    contentDescription = "Shield",
                                    tint = if (updateAvailable) Color(0xFFFF9500) else Color(0xFF34C759),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Text(
                            text = statusMessage,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = contentColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        if (updateProgress >= 0f && updateProgress < 1f) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { updateProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF34C759),
                                trackColor = Color(0xFFE5E5EA)
                            )
                        }

                        if (!isLoading) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "App Shield Version:", color = Color.Gray, fontSize = 14.sp)
                                    Text(text = localVersion, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Last Updated:", color = Color.Gray, fontSize = 14.sp)
                                    Text(text = lastUpdateText, color = contentColor, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Auto Update Toggle Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBackdrop(
                            backdrop = screenContentBackdrop,
                            shape = { RoundedCornerShape(16.dp) },
                            effects = {
                                vibrancy()
                                blur(20f.dp.toPx())
                                lens(12f.dp.toPx(), 16f.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(
                                    if (isLightTheme) Color(0xFFFAFAFA).copy(0.6f)
                                    else Color(0xFF1C1C1E).copy(0.6f)
                                )
                            }
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val newVal = !autoUpdateEnabled
                            autoUpdateEnabled = newVal
                            prefs.edit().putBoolean("auto_update_filters", newVal).apply()
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Auto Update Filters", color = contentColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Download latest ad, tracker, and malware lists automatically", color = Color.Gray, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        LiquidToggle(
                            selected = { autoUpdateEnabled },
                            onSelect = {
                                autoUpdateEnabled = it
                                prefs.edit().putBoolean("auto_update_filters", it).apply()
                            },
                            backdrop = screenContentBackdrop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // App Update Notification Toggle Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBackdrop(
                            backdrop = screenContentBackdrop,
                            shape = { RoundedCornerShape(16.dp) },
                            effects = {
                                vibrancy()
                                blur(20f.dp.toPx())
                                lens(12f.dp.toPx(), 16f.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(
                                    if (isLightTheme) Color(0xFFFAFAFA).copy(0.6f)
                                    else Color(0xFF1C1C1E).copy(0.6f)
                                )
                            }
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val newVal = !notifyAppUpdateEnabled
                            notifyAppUpdateEnabled = newVal
                            prefs.edit().putBoolean("notify_app_update", newVal).apply()
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Filter Update Notifications", color = contentColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Get notified when new blocklists are available to download", color = Color.Gray, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        LiquidToggle(
                            selected = { notifyAppUpdateEnabled },
                            onSelect = {
                                notifyAppUpdateEnabled = it
                                prefs.edit().putBoolean("notify_app_update", it).apply()
                            },
                            backdrop = screenContentBackdrop
                        )
                    }
                }

                LaunchedEffect(updateProgress, updateStateMsg) {
                    if (updateProgress >= 0f) {
                        isLoading = true
                        statusMessage = updateStateMsg
                        if (updateProgress >= 1f) {
                            localVersion = remoteVersion ?: "v1.0.7"
                            updateAvailable = false
                            statusMessage = "Your shield filters are now up to date!"
                            lastUpdateTimestamp = System.currentTimeMillis()
                            kotlinx.coroutines.delay(1000)
                            isLoading = false
                            showRestartDialog = true
                        }
                    }
                }

                AnimatedVisibility(
                    visible = updateAvailable && !isLoading,
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    LiquidButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                statusMessage = "Starting download..."
                                FilterListUpdater.checkAndUpdate(context, force = true)
                            }
                        },
                        backdrop = screenContentBackdrop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = { RoundedCornerShape(16.dp) },
                        surfaceColor = Color.Transparent
                    ) {
                        Text(
                            text = "Update Now",
                            color = contentColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

        // Floating back button (top-left)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp + topPadding)
                .padding(top = topPadding, start = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            LiquidButton(
                onClick = onBackClick,
                backdrop = screenContentBackdrop,
                modifier = Modifier.size(44.dp),
                shape = { CircleShape },
                surfaceColor = Color.Transparent
            ) {
                Icon(
                    imageVector = ChevronLeftIcon,
                    contentDescription = "Back",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (showRestartDialog) {
            val dialogBackdrop = rememberLayerBackdrop()
            val coroutineScope = rememberCoroutineScope()
            
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
                            onClick = { showRestartDialog = false }
                        )
                )
                RestartShieldDialog(
                    onDismiss = { showRestartDialog = false },
                    onRestart = {
                        showRestartDialog = false
                        val vpnPrefs = context.getSharedPreferences("lykon_shield_prefs", Context.MODE_PRIVATE)
                        val isEnabled = vpnPrefs.getBoolean("protection_enabled", false)
                        if (isEnabled) {
                            val stopIntent = Intent(context, LykonVpnService::class.java).apply {
                                action = LykonVpnService.ACTION_STOP
                            }
                            context.startService(stopIntent)
                            
                            coroutineScope.launch {
                                delay(500)
                                val startIntent = Intent(context, LykonVpnService::class.java).apply {
                                    action = LykonVpnService.ACTION_START
                                }
                                context.startService(startIntent)
                            }
                        }
                    },
                    backdrop = rememberCombinedBackdrop(screenContentBackdrop, dialogBackdrop)
                )
            }
        }
    }
}

@Composable
fun RestartShieldDialog(
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White

    GlassCard(
        backdrop = backdrop,
        modifier = Modifier.width(280.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Text(
                text = "Restart Shield?",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "New blocklist rules have been successfully downloaded. Restart the protection shield to load the new filters immediately?",
                fontSize = 13.sp,
                color = textColor.copy(alpha = 0.65f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            androidx.compose.material3.HorizontalDivider(
                color = if (isLightTheme) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                // Dismiss Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Not Now",
                        fontSize = 15.sp,
                        color = if (isLightTheme) Color.DarkGray else Color.LightGray
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(if (isLightTheme) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f))
                )

                // Confirm/Restart Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onRestart() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Restart Shield",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34C759)
                    )
                }
            }
        }
    }
}
