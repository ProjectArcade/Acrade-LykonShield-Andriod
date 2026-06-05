package com.arcadesoftware.lykonshield

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.effects.lens
import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Size


@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val isLightTheme = LocalIsLightTheme.current
    val containerColor = if (isLightTheme) {
        Color(0xFFFFFFFF).copy(alpha = 0.12f)
    } else {
        Color(0xFF1E1E1E).copy(alpha = 0.16f)
    }

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(24f.dp.toPx())
                },
                highlight = {
                    Highlight.Default.copy(
                        alpha = if (isLightTheme) 0.35f else 0.15f
                    )
                },
                shadow = {
                    Shadow(
                        color = Color.Black,
                        radius = 12f.dp,
                        alpha = if (isLightTheme) 0.03f else 0.1f
                    )
                },
                onDrawSurface = {
                    drawRect(containerColor)
                    // Draw a thin semi-transparent white border to simulate glossy glass bevel
                    val strokeColor = if (isLightTheme) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f)
                    val outline = shape.createOutline(size, layoutDirection, density = this)
                    val outlinePath = when (outline) {
                        is androidx.compose.ui.graphics.Outline.Rectangle -> {
                            Path().apply { addRect(outline.rect) }
                        }
                        is androidx.compose.ui.graphics.Outline.Rounded -> {
                            Path().apply { addRoundRect(outline.roundRect) }
                        }
                        is androidx.compose.ui.graphics.Outline.Generic -> {
                            outline.path
                        }
                    }
                    drawPath(
                        path = outlinePath,
                        color = strokeColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f.dp.toPx())
                    )
                }
            ),
        content = content
    )
}

@Composable
fun AppIconImage(drawable: android.graphics.drawable.Drawable, modifier: Modifier = Modifier) {
    val bitmap = remember(drawable) {
        val bmp = android.graphics.Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp
    }
    androidx.compose.foundation.Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}

@Composable
fun HomeScreen(
    state: LazyListState,
    isProtectionEnabled: Boolean,
    onProtectionToggle: () -> Unit,
    onExcludeAppsClick: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val localBackdrop = rememberLayerBackdrop()

    val context = LocalContext.current
    val pm = context.packageManager
    var topApps by remember { mutableStateOf<List<Pair<String, android.graphics.drawable.Drawable?>>>(emptyList()) }
    var totalProtectedApps by remember { mutableStateOf(24) }

    // Read list of non-system apps only once when the screen opens to optimize performance
    val installedApps = remember { mutableStateListOf<Pair<String, String>>() }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val packages = pm.getInstalledPackages(0)
                val filtered = packages.mapNotNull { pkg ->
                    val appInfo = pkg.applicationInfo
                    if (appInfo != null && (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 && pkg.packageName != context.packageName) {
                        val name = appInfo.loadLabel(pm).toString()
                        Pair(pkg.packageName, name)
                    } else null
                }.sortedBy { it.second.lowercase() }
                withContext(Dispatchers.Main) {
                    installedApps.clear()
                    installedApps.addAll(filtered)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(ShieldStatsManager.appBlockCounts.size, ShieldStatsManager.totalBlockedTrackers, installedApps.size) {
        if (installedApps.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                // Get all apps that have blocked counts
                val blockedAppsMap = ShieldStatsManager.appBlockCounts.toMap()
                val sortedPackages = blockedAppsMap.entries
                    .sortedByDescending { it.value }
                    .map { it.key }
                
                val finalAppList = mutableListOf<Pair<String, android.graphics.drawable.Drawable?>>()
                
                // Excluded list
                val excludedApps = context.getSharedPreferences("lykon_shield_prefs", Context.MODE_PRIVATE)
                    .getStringSet("excluded_apps", emptySet()) ?: emptySet()
                
                totalProtectedApps = (installedApps.size - excludedApps.size).coerceAtLeast(0)

                // 1. Process active apps with blocked counts first
                for (pkg in sortedPackages) {
                    val appInfo = installedApps.firstOrNull { it.first == pkg }
                    if (appInfo != null) {
                        try {
                            val icon = pm.getApplicationIcon(pkg)
                            finalAppList.add(Pair(appInfo.second, icon))
                        } catch (e: Exception) {
                            finalAppList.add(Pair(appInfo.second, null))
                        }
                    }
                }
                
                // 2. Fill the remaining spots up to 5 with other protected apps
                for (app in installedApps) {
                    if (finalAppList.size >= 5) break
                    if (!blockedAppsMap.containsKey(app.first)) {
                        try {
                            val icon = pm.getApplicationIcon(app.first)
                            finalAppList.add(Pair(app.second, icon))
                        } catch (e: Exception) {
                            finalAppList.add(Pair(app.second, null))
                        }
                    }
                }
                
                // 3. Fallbacks if empty
                if (finalAppList.isEmpty()) {
                    finalAppList.addAll(
                        listOf(
                            Pair("Chrome", null),
                            Pair("YouTube", null),
                            Pair("Instagram", null),
                            Pair("WhatsApp", null),
                            Pair("Spotify", null)
                        )
                    )
                }

                val resultList = finalAppList.take(5)
                withContext(Dispatchers.Main) {
                    topApps = resultList
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding + 16.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Home",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        item {
            UnifiedShieldCard(
                enabled = isProtectionEnabled,
                onToggle = onProtectionToggle,
                backdrop = backdrop
            )
        }

        if (isProtectionEnabled) {
            item {
                InAppTrackerProtectionCard(
                    isProtectionEnabled = isProtectionEnabled,
                    onExcludeAppsClick = onExcludeAppsClick,
                    topApps = topApps,
                    totalProtectedApps = totalProtectedApps,
                    backdrop = backdrop
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PROTECTION STATISTICS",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
                GlassCard(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val trackersVal = if (isProtectionEnabled) ShieldStatsManager.totalBlockedTrackers.toString() else "0"
                        val adsVal = if (isProtectionEnabled) ShieldStatsManager.totalAdsBlocked.toString() else "0"
                        
                        val dataSavedVal = if (isProtectionEnabled) {
                            val bytes = ShieldStatsManager.totalDataSavedBytes
                            when {
                                bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes.toFloat() / (1024 * 1024 * 1024))
                                bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes.toFloat() / (1024 * 1024))
                                else -> String.format("%.1f KB", bytes.toFloat() / 1024)
                            }
                        } else "0 KB"

                        StatsRow(title = "Blocked Trackers", value = trackersVal, color = Color(0xFFFF3B30))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(if (isLightTheme) Color(0xFFE5E5EA).copy(0.5f) else Color(0xFF38383A).copy(0.5f))
                        )
                        StatsRow(title = "Ads Blocked", value = adsVal, color = Color(0xFFFF9500))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(if (isLightTheme) Color(0xFFE5E5EA).copy(0.5f) else Color(0xFF38383A).copy(0.5f))
                        )
                        StatsRow(title = "Data Saved", value = dataSavedVal, color = Color(0xFF34C759))
                    }
                }
            }
        }
    }
}

class ShieldShape : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            moveTo(w * 0.5f, h * 0.08f)
            // Left curve
            cubicTo(w * 0.15f, h * 0.08f, w * 0.08f, h * 0.45f, w * 0.08f, h * 0.55f)
            cubicTo(w * 0.08f, h * 0.8f, w * 0.45f, h * 0.95f, w * 0.5f, h * 0.98f)
            // Right curve
            cubicTo(w * 0.55f, h * 0.95f, w * 0.92f, h * 0.8f, w * 0.92f, h * 0.55f)
            cubicTo(w * 0.92f, h * 0.45f, w * 0.85f, h * 0.08f, w * 0.5f, h * 0.08f)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

@Composable
fun UnifiedShieldCard(
    enabled: Boolean,
    onToggle: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = LocalIsLightTheme.current
    val isLiquidGlass = LocalIsLiquidGlassEnabled.current
    val contentColor = if (isLightTheme) Color.Black else Color.White

    val engineState = com.arcadesoftware.lykon.AdblockEngine.observableState.value
    val isWarmingUp = enabled && engineState == com.arcadesoftware.lykon.AdblockEngine.EngineState.INITIALIZING

    // Card Ambient Glow Animation (breath-like scale & alpha pulsing)
    val cardGlowTransition = rememberInfiniteTransition(label = "cardAmbientGlow")
    val cardGlowScale by cardGlowTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "cardGlowScale"
    )
    val cardGlowAlpha by cardGlowTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "cardGlowAlpha"
    )

    // Pulse animation for warming up state
    val pulseTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseAlphaAnimation"
    )

    val cardGlowModifier = modifier
        .fillMaxWidth()
        .drawBehind {
            val glowColor = if (isWarmingUp) Color(0xFFFF9500) else if (enabled) Color(0xFF34C759) else Color(0xFFFF3B30)
            
            // Render a beautiful, soft gaussian-blurred glow that emerges from behind the card container
            val paint = android.graphics.Paint().apply {
                color = glowColor.toArgb()
                alpha = (cardGlowAlpha * 255).toInt()
                isAntiAlias = true
                maskFilter = BlurMaskFilter(24f.dp.toPx() * cardGlowScale, BlurMaskFilter.Blur.NORMAL)
            }
            
            drawIntoCanvas { canvas ->
                // Draw blurred glow rectangle slightly larger than the card bounds
                canvas.nativeCanvas.drawRoundRect(
                    -8f.dp.toPx(),
                    -8f.dp.toPx(),
                    size.width + 8f.dp.toPx(),
                    size.height + 8f.dp.toPx(),
                    24f.dp.toPx(),
                    24f.dp.toPx(),
                    paint
                )
            }
        }
    
    GlassCard(
        backdrop = backdrop,
        modifier = cardGlowModifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Section: Glowing Shield + Status Text
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Liquid Glass Shield with pulsing glow
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Glowing background
                    val infiniteTransition = rememberInfiniteTransition(label = "shieldGlow")
                    val glowScale by infiniteTransition.animateFloat(
                        initialValue = 0.9f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ),
                        label = "glowScale"
                    )
                    val glowAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 0.65f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ),
                        label = "glowAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .graphicsLayer {
                                scaleX = glowScale
                                scaleY = glowScale
                                alpha = glowAlpha
                            }
                            .drawBehind {
                                val glowColor = if (isWarmingUp) Color(0xFFFF9500) else if (enabled) Color(0xFF34C759) else Color(0xFFFF3B30)
                                drawCircle(
                                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                        colors = listOf(
                                            glowColor.copy(alpha = 0.7f),
                                            glowColor.copy(alpha = 0.15f),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = size.minDimension * 0.55f
                                    )
                                )
                            }
                    )

                    // Shadow layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val w = size.width
                                val h = size.height
                                val shadowPath = Path().apply {
                                    moveTo(w * 0.5f, h * 0.08f)
                                    // Left curve
                                    cubicTo(w * 0.15f, h * 0.08f, w * 0.08f, h * 0.45f, w * 0.08f, h * 0.55f)
                                    cubicTo(w * 0.08f, h * 0.8f, w * 0.45f, h * 0.95f, w * 0.5f, h * 0.98f)
                                    // Right curve
                                    cubicTo(w * 0.55f, h * 0.95f, w * 0.92f, h * 0.8f, w * 0.92f, h * 0.55f)
                                    cubicTo(w * 0.92f, h * 0.45f, w * 0.85f, h * 0.08f, w * 0.5f, h * 0.08f)
                                    close()
                                }
                                drawPath(
                                    path = shadowPath,
                                    color = Color.Black.copy(alpha = if (isLightTheme) 0.12f else 0.3f)
                                )
                            }
                    )

                    // Liquid Glass Refracting Shield
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(ShieldShape())
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(0.dp) },
                                effects = {
                                    vibrancy()
                                    blur(10f.dp.toPx())
                                    if (isLiquidGlass) {
                                        lens(
                                            16f.dp.toPx(),
                                            10f.dp.toPx(),
                                            chromaticAberration = true
                                        )
                                    } else {
                                        lens(
                                            8f.dp.toPx(),
                                            5f.dp.toPx(),
                                            chromaticAberration = true
                                        )
                                    }
                                },
                                highlight = { null },
                                shadow = { null },
                                innerShadow = { null },
                                onDrawSurface = {
                                    // Subtle tint color on the surface of the glass shield
                                    val tintColor = if (isWarmingUp) {
                                        Color(0xFFFF9500).copy(alpha = 0.15f * pulseAlpha)
                                    } else if (enabled) {
                                        Color(0xFF34C759).copy(alpha = 0.15f)
                                    } else {
                                        Color(0xFFFF3B30).copy(alpha = 0.15f)
                                    }
                                    drawRect(tintColor)

                                    val w = size.width
                                    val h = size.height

                                    // 1. directional highlight on the left edge (simulating top-left light source)
                                    val highlightPath = Path().apply {
                                        moveTo(w * 0.5f, h * 0.08f)
                                        cubicTo(w * 0.15f, h * 0.08f, w * 0.08f, h * 0.45f, w * 0.08f, h * 0.55f)
                                        cubicTo(w * 0.08f, h * 0.8f, w * 0.45f, h * 0.95f, w * 0.5f, h * 0.98f)
                                    }
                                    drawPath(
                                        path = highlightPath,
                                        color = Color.White.copy(alpha = if (isLightTheme) 0.55f else 0.28f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 2f.dp.toPx(),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                    )

                                    // 2. directional shadow on the right edge
                                    val shadowEdgePath = Path().apply {
                                        moveTo(w * 0.5f, h * 0.98f)
                                        cubicTo(w * 0.55f, h * 0.95f, w * 0.92f, h * 0.8f, w * 0.92f, h * 0.55f)
                                        cubicTo(w * 0.92f, h * 0.45f, w * 0.85f, h * 0.08f, w * 0.5f, h * 0.08f)
                                    }
                                    drawPath(
                                        path = shadowEdgePath,
                                        color = Color.Black.copy(alpha = 0.18f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 2f.dp.toPx(),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                    )

                                    // 2.5 Diagonal gloss reflections (polished glass sweep)
                                    val glossPath1 = Path().apply {
                                        moveTo(w * 0.35f, h * 0.14f)
                                        lineTo(w * 0.65f, h * 0.86f)
                                    }
                                    drawPath(
                                        path = glossPath1,
                                        color = Color.White.copy(alpha = if (isLightTheme) 0.38f else 0.18f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 6f.dp.toPx(),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                    )
                                    val glossPath2 = Path().apply {
                                        moveTo(w * 0.45f, h * 0.14f)
                                        lineTo(w * 0.75f, h * 0.86f)
                                    }
                                    drawPath(
                                        path = glossPath2,
                                        color = Color.White.copy(alpha = if (isLightTheme) 0.24f else 0.12f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 2f.dp.toPx(),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                    )

                                    // 3. Inner shield outline
                                    val borderPath = Path().apply {
                                        moveTo(w * 0.5f, h * 0.14f)
                                        cubicTo(w * 0.22f, h * 0.14f, w * 0.16f, h * 0.46f, w * 0.16f, h * 0.54f)
                                        cubicTo(w * 0.16f, h * 0.74f, w * 0.46f, h * 0.88f, w * 0.5f, h * 0.9f)
                                        cubicTo(w * 0.54f, h * 0.88f, w * 0.84f, h * 0.74f, w * 0.84f, h * 0.54f)
                                        cubicTo(w * 0.84f, h * 0.46f, w * 0.78f, h * 0.14f, w * 0.5f, h * 0.14f)
                                        close()
                                    }
                                    
                                    val borderColor = if (isWarmingUp) {
                                        Color(0xFFFF9500).copy(alpha = pulseAlpha)
                                    } else if (enabled) {
                                        Color(0xFF34C759).copy(alpha = 0.75f)
                                    } else {
                                        Color(0xFFFF3B30).copy(alpha = 0.75f)
                                    }
                                    drawPath(
                                        path = borderPath,
                                        color = borderColor,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 2.5f.dp.toPx(),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )

                                    // 4. Symbol in center of shield (star when warming up, checkmark when active, exclamation mark when disarmed)
                                    if (enabled) {
                                        if (isWarmingUp) {
                                            val starPath = Path().apply {
                                                val cx = w * 0.5f
                                                val cy = h * 0.49f
                                                val spikes = 5
                                                val outerRadius = w * 0.15f
                                                val innerRadius = w * 0.065f
                                                var rot = -Math.PI / 2
                                                val step = Math.PI / spikes

                                                moveTo(cx + Math.cos(rot).toFloat() * outerRadius, cy + Math.sin(rot).toFloat() * outerRadius)
                                                for (i in 0 until spikes) {
                                                    rot += step
                                                    lineTo(cx + Math.cos(rot).toFloat() * innerRadius, cy + Math.sin(rot).toFloat() * innerRadius)
                                                    rot += step
                                                    lineTo(cx + Math.cos(rot).toFloat() * outerRadius, cy + Math.sin(rot).toFloat() * outerRadius)
                                                }
                                                close()
                                            }
                                            drawPath(
                                                path = starPath,
                                                color = Color.White.copy(alpha = 0.95f),
                                                style = androidx.compose.ui.graphics.drawscope.Fill
                                            )
                                        } else {
                                            val checkPath = Path().apply {
                                                moveTo(w * 0.38f, h * 0.50f)
                                                lineTo(w * 0.47f, h * 0.59f)
                                                lineTo(w * 0.63f, h * 0.40f)
                                            }
                                            drawPath(
                                                path = checkPath,
                                                color = Color.White.copy(alpha = 0.9f),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = 3.5f.dp.toPx(),
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                )
                                            )
                                        }
                                    } else {
                                        // Line of exclamation point
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.9f),
                                            start = Offset(w * 0.5f, h * 0.36f),
                                            end = Offset(w * 0.5f, h * 0.53f),
                                            strokeWidth = 3.5f.dp.toPx(),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                        // Dot of exclamation point
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.9f),
                                            center = Offset(w * 0.5f, h * 0.63f),
                                            radius = 2.5f.dp.toPx()
                                        )
                                    }
                                }
                            )
                    )
                }

                // Status messages
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isWarmingUp) {
                            "Lykon Shield is warming up..."
                        } else if (enabled) {
                            "Protection Active"
                        } else {
                            "Shield Disarmed"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isWarmingUp) {
                            "Verifying blocking sites and trackers..."
                        } else if (enabled) {
                            "Blocking active ad & tracking domains"
                        } else {
                            "Your internet traffic is unencrypted and vulnerable"
                        },
                        color = Color.Gray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(if (isLightTheme) Color(0xFFE5E5EA).copy(0.5f) else Color(0xFF38383A).copy(0.5f))
            )

            // Bottom Section: Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Lykon Shield Protection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = contentColor
                    )
                    Text(
                        text = if (isWarmingUp) "Initializing Bloom filter cache..." else if (enabled) "System is active and protecting" else "Tap/drag switch to secure your traffic",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                
                val toggleBackdrop = rememberLayerBackdrop()
                LiquidToggle(
                    selected = { enabled },
                    onSelect = { onToggle() },
                    backdrop = toggleBackdrop
                )
            }
        }
    }
}

@Composable
fun InAppTrackerProtectionCard(
    isProtectionEnabled: Boolean,
    onExcludeAppsClick: () -> Unit,
    topApps: List<Pair<String, android.graphics.drawable.Drawable?>>,
    totalProtectedApps: Int,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    
    GlassCard(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // No card header row as requested (directly showing Protected Applications section)
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PROTECTED APPLICATIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (topApps.isEmpty()) {
                        Text(
                            text = "Loading installed apps...",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    } else {
                        topApps.forEach { app ->
                            Box(modifier = Modifier.size(32.dp)) {
                                if (app.second != null) {
                                    AppIconImage(drawable = app.second!!)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = app.first.take(1),
                                            color = contentColor.copy(0.6f),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (isProtectionEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(if (isLightTheme) Color.White else Color(0xFF1E1E1E))
                                            .padding(1.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(Color(0xFF34C759))
                                        )
                                    }
                                }
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isLightTheme) Color.Black.copy(0.05f) else Color.White.copy(0.1f))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isProtectionEnabled) "+${(totalProtectedApps - topApps.size).coerceAtLeast(0)} more" else "Bypassed",
                                color = contentColor.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(if (isLightTheme) Color(0xFFE5E5EA).copy(0.5f) else Color(0xFF38383A).copy(0.5f))
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExcludeAppsClick() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manage Protected Apps & Bypass Rules",
                    fontSize = 14.sp,
                    color = if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF),
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun StatsRow(
    title: String,
    value: String,
    color: Color
) {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(text = title, color = textColor, fontSize = 16.sp)
        }
        Text(text = value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
