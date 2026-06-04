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

@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val isLightTheme = LocalIsLightTheme.current
    val containerColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.45f)
    } else {
        Color(0xFF1E1E1E).copy(alpha = 0.45f)
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
                innerShadow = {
                    InnerShadow(
                        radius = 1.dp,
                        color = if (isLightTheme) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                        alpha = 1f
                    )
                },
                onDrawSurface = {
                    drawRect(containerColor)
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

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val packages = pm.getInstalledPackages(0)
                val filtered = packages.mapNotNull { pkg ->
                    val appInfo = pkg.applicationInfo
                    if (appInfo != null) {
                        val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        if (!isSystem) {
                            val name = appInfo.loadLabel(pm).toString()
                            val icon = appInfo.loadIcon(pm)
                            Pair(name, icon)
                        } else null
                    } else null
                }.sortedBy { it.first.lowercase() }

                val finalApps = if (filtered.isEmpty()) {
                    listOf(
                        Pair("Chrome", null),
                        Pair("YouTube", null),
                        Pair("Instagram", null),
                        Pair("WhatsApp", null),
                        Pair("Spotify", null)
                    )
                } else {
                    filtered
                }.take(5)

                withContext(Dispatchers.Main) {
                    topApps = finalApps
                }
            } catch (e: Exception) {
                // Keep default empty/mock if permission or system call fails
            }
        }
    }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding + 16.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // No header text block ("Shield" text has been removed as requested)

        item {
            ShieldStatusCard(enabled = isProtectionEnabled, backdrop = backdrop)
        }

        item {
            GlassCard(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Shield Protection",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = contentColor
                        )
                        Text(
                            text = if (isProtectionEnabled) "System is active and protecting" else "Tap/drag switch to secure your traffic",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                    LiquidToggle(
                        selected = { isProtectionEnabled },
                        onSelect = { onProtectionToggle() },
                        backdrop = localBackdrop
                    )
                }
            }
        }

        item {
            InAppTrackerProtectionCard(
                isProtectionEnabled = isProtectionEnabled,
                onExcludeAppsClick = onExcludeAppsClick,
                topApps = topApps,
                backdrop = backdrop
            )
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
                        StatsRow(title = "Blocked Trackers", value = if (isProtectionEnabled) "1,248" else "0", color = Color(0xFFFF3B30))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(if (isLightTheme) Color(0xFFE5E5EA).copy(0.5f) else Color(0xFF38383A).copy(0.5f))
                        )
                        StatsRow(title = "Ads Blocked", value = if (isProtectionEnabled) "3,824" else "0", color = Color(0xFFFF9500))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(if (isLightTheme) Color(0xFFE5E5EA).copy(0.5f) else Color(0xFF38383A).copy(0.5f))
                        )
                        StatsRow(title = "Data Saved", value = if (isProtectionEnabled) "452 MB" else "0 MB", color = Color(0xFF34C759))
                    }
                }
            }
        }
    }
}

@Composable
fun ShieldStatusCard(enabled: Boolean, backdrop: Backdrop) {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White
    val statusColor = if (enabled) Color(0xFF34C759) else Color(0xFFFF3B30)

    GlassCard(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (enabled) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                        ),
                        label = "scale"
                    )
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                        ),
                        label = "alpha"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                                alpha = pulseAlpha
                            }
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.6f))
                    )
                }
            }
            Column {
                Text(
                    text = if (enabled) "Protection Active" else "Shield Disarmed",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = textColor
                )
                Text(
                    text = if (enabled) "Blocking active ad & tracking domains" else "Your internet traffic is unencrypted and vulnerable",
                    color = Color.Gray,
                    fontSize = 13.sp
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
                                text = if (isProtectionEnabled) "+24 more" else "Bypassed",
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
