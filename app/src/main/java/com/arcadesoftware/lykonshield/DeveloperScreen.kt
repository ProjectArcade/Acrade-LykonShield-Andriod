package com.arcadesoftware.lykonshield

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

@Composable
fun DeveloperScreen(
    navController: androidx.navigation.NavController,
    isAdvancedNetworkStatsEnabled: Boolean,
    onAdvancedNetworkStatsToggle: (Boolean) -> Unit,
    isLiquidGlassEnabled: Boolean,
    onLiquidGlassToggle: (Boolean) -> Unit,
    isCustomShadersEnabled: Boolean,
    onCustomShadersToggle: (Boolean) -> Unit,
    backdropBlurRadius: Float,
    onBackdropBlurRadiusChange: (Float) -> Unit,
    isHighlightCapturesEnabled: Boolean,
    onHighlightCapturesToggle: (Boolean) -> Unit,
    isForceSystemBlurEnabled: Boolean,
    onForceSystemBlurToggle: (Boolean) -> Unit,
    renderCacheSize: Int,
    onRenderCacheSizeChange: (Int) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    backdrop: Backdrop
) {
    val screenContentBackdrop = rememberLayerBackdrop()
    val dialogBackdrop = rememberLayerBackdrop()
    val isLightTheme = LocalIsLightTheme.current
    val blurRadius = LocalBackdropBlurRadius.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    var showBlurDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalIsLiquidGlassEnabled provides true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isLightTheme) Color(0xFFF2F2F7) else Color.Black)
        ) {
        Box(
            modifier = Modifier
                .layerBackdrop(screenContentBackdrop)
                .layerBackdrop(dialogBackdrop)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topPadding + 64.dp, bottom = bottomPadding + 32.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Text(
                        text = "Developer Options",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "SHADER CONSOLE",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        GlassCard(
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SettingsRow(
                                    title = "AGSL Status",
                                    value = if (isCustomShadersEnabled) "Active" else "Optimized (Off)",
                                    showDivider = true
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showCacheDialog = true }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Render Cache size", color = contentColor, fontSize = 16.sp)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${renderCacheSize} MB",
                                            color = Color.Gray,
                                            fontSize = 16.sp
                                        )
                                        Text(text = "〉", color = Color.Gray, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "APP OPTIMIZATION",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        GlassCard(
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SettingsSwitchRow(
                                    title = "Custom Shaders",
                                    checked = isCustomShadersEnabled,
                                    onCheckedChange = onCustomShadersToggle,
                                    backdrop = backdrop,
                                    showDivider = true
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showBlurDialog = true }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Backdrop Blur Radius", color = contentColor, fontSize = 16.sp)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = if (backdropBlurRadius == 0f) "Off (0dp)" else "${backdropBlurRadius.toInt()}dp",
                                            color = Color.Gray,
                                            fontSize = 16.sp
                                        )
                                        Text(text = "〉", color = Color.Gray, fontSize = 14.sp)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .padding(start = 16.dp)
                                        .background(if (isLightTheme) Color(0xFFC7C7CC) else Color(0xFF38383A))
                                )
                                SettingsSwitchRow(
                                    title = "Liquid Glass Shaders",
                                    checked = isLiquidGlassEnabled,
                                    onCheckedChange = onLiquidGlassToggle,
                                    backdrop = backdrop,
                                    showDivider = false
                                )
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ADVANCED FEATURES",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        GlassCard(
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SettingsSwitchRow(
                                    title = "Network Hosts & Traffic Stream",
                                    checked = isAdvancedNetworkStatsEnabled,
                                    onCheckedChange = onAdvancedNetworkStatsToggle,
                                    backdrop = backdrop,
                                    showDivider = false
                                )
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DEBUG TOOLS",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        GlassCard(
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SettingsSwitchRow(
                                    title = "Highlight Captures",
                                    checked = isHighlightCapturesEnabled,
                                    onCheckedChange = onHighlightCapturesToggle,
                                    backdrop = backdrop,
                                    showDivider = true
                                )
                                SettingsSwitchRow(
                                    title = "Force System Blur",
                                    checked = isForceSystemBlurEnabled,
                                    onCheckedChange = onForceSystemBlurToggle,
                                    backdrop = backdrop,
                                    showDivider = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full width blurred header top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp + topPadding)
                .drawBackdrop(
                    backdrop = screenContentBackdrop,
                    shape = { RectangleShape },
                    effects = {
                        vibrancy()
                        if (blurRadius > 0f) {
                            blur(blurRadius.dp.toPx())
                        }
                    },
                    onDrawSurface = {
                        drawRect(if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f) else Color(0xFF121212).copy(0.4f))
                    }
                )
                .padding(top = topPadding, start = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            LiquidButton(
                onClick = { navController.popBackStack() },
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

        // ios-style modal blur radius dialog
        if (showBlurDialog) {
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
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showBlurDialog = false }
                        )
                )
                IosBlurRadiusDialog(
                    currentRadius = backdropBlurRadius,
                    onRadiusSelect = onBackdropBlurRadiusChange,
                    onDismiss = { showBlurDialog = false },
                    backdrop = rememberCombinedBackdrop(backdrop, dialogBackdrop)
                )
            }
        }

        // ios-style modal render cache size dialog
        if (showCacheDialog) {
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
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showCacheDialog = false }
                        )
                )
                IosCacheSizeDialog(
                    currentSize = renderCacheSize,
                    onSizeSelect = onRenderCacheSizeChange,
                    onDismiss = { showCacheDialog = false },
                    backdrop = rememberCombinedBackdrop(backdrop, dialogBackdrop)
                )
            }
        }
    }
}
}

@Composable
fun IosBlurRadiusDialog(
    currentRadius: Float,
    onRadiusSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White
    val systemBlue = if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF)

    GlassCard(
        backdrop = backdrop,
        modifier = Modifier.width(320.dp),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Blur Radius",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose backdrop blur level",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isLightTheme) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.1f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.1f))
            )

            val options = listOf(
                Pair("Off (0dp)", 0f),
                Pair("Low (8dp)", 8f),
                Pair("Medium (16dp)", 16f),
                Pair("High (24dp)", 24f),
                Pair("Ultra (36dp)", 36f)
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                options.forEachIndexed { index, pair ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(if (isLightTheme) Color.Black.copy(0.06f) else Color.White.copy(0.08f))
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onRadiusSelect(pair.second)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pair.first,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                        if (currentRadius == pair.second) {
                            Text(
                                text = "✓",
                                color = systemBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.1f))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(systemBlue)
                    .clickable { onDismiss() }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun IosCacheSizeDialog(
    currentSize: Int,
    onSizeSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White
    val systemBlue = if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF)

    GlassCard(
        backdrop = backdrop,
        modifier = Modifier.width(320.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Render Cache Size",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose memory limit for shaders",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isLightTheme) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.1f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.1f))
            )

            val options = listOf(
                Pair("128 MB", 128),
                Pair("256 MB", 256),
                Pair("512 MB", 512),
                Pair("1024 MB", 1024)
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                options.forEachIndexed { index, pair ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(if (isLightTheme) Color.Black.copy(0.06f) else Color.White.copy(0.08f))
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSizeSelect(pair.second)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pair.first,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                        if (currentSize == pair.second) {
                            Text(
                                text = "✓",
                                color = systemBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.1f))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(systemBlue)
                    .clickable { onDismiss() }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
