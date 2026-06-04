package com.arcadesoftware.lykonshield

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BlockedScreen(
    state: LazyListState,
    isProtectionEnabled: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val context = LocalContext.current
    val pm = context.packageManager

    var selectedTab by remember { mutableStateOf(0) } // 0 = Apps, 1 = Network

    // Load active apps with JNI details
    var blockedAppList by remember { mutableStateOf<List<Triple<String, Drawable?, Int>>>(emptyList()) }
    val appInfoCache = remember { mutableMapOf<String, Pair<String, Drawable?>>() }

    LaunchedEffect(ShieldStatsManager.appBlockCounts.size, ShieldStatsManager.totalBlockedTrackers) {
        withContext(Dispatchers.IO) {
            val appCounts = ShieldStatsManager.appBlockCounts.toMap()
            val sortedList = appCounts.entries
                .sortedByDescending { it.value }
                .map { entry ->
                    val pkg = entry.key
                    val count = entry.value
                    
                    val cached = appInfoCache[pkg]
                    if (cached != null) {
                        Triple(cached.first, cached.second, count)
                    } else {
                        var label = pkg.substringAfterLast('.')
                        var icon: Drawable? = null
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            label = pm.getApplicationLabel(appInfo).toString()
                            icon = pm.getApplicationIcon(appInfo)
                            appInfoCache[pkg] = Pair(label, icon)
                        } catch (e: Exception) {
                            // Keep package shortname
                        }
                        Triple(label, icon, count)
                    }
                }
            withContext(Dispatchers.Main) {
                blockedAppList = sortedList
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
                text = "Blocked",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        if (!isProtectionEnabled) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(if (isLightTheme) Color(0xFFE5E5EA) else Color(0xFF1C1C1E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Shield Lock",
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Text(
                            text = "Shield Off",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Text(
                            text = "Turn on Shield to start blocking trackers and ads.",
                            fontSize = 15.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        } else {
            // Liquid Glass Graph Card
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "BLOCK HISTORY OVER TIME",
                        color = Color.Gray,
                        fontSize = 11.sp,
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
                            Text(
                                text = "Real-time Interception Rate",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            
                            val historyPoints = ShieldStatsManager.hourlyBlockHistory.toList()
                            if (historyPoints.isNotEmpty()) {
                                LiquidGlassGraph(
                                    history = historyPoints,
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    lineColor = Color(0xFFFF3B30),
                                    fillColor = Color(0xFFFF3B30).copy(alpha = 0.15f)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    contentAlignment = Alignment.Center
                               ) {
                                   Text("Waiting for network activity...", color = Color.Gray, fontSize = 13.sp)
                               }
                            }
                        }
                    }
                }
            }

            // Daily Bar Chart Card
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DAILY ACTIVITY (LAST 7 DAYS)",
                        color = Color.Gray,
                        fontSize = 11.sp,
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
                            DailyBarChart(
                                dailyHistory = ShieldStatsManager.dailyBlockHistory,
                                isLightTheme = isLightTheme
                            )
                        }
                    }
                }
            }

            // Category breakdown Pie/Donut Chart Card
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "THREAT CATEGORY BREAKDOWN",
                        color = Color.Gray,
                        fontSize = 11.sp,
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
                            CategoryPieChart(
                                categoryCounts = ShieldStatsManager.categoryBlockCounts,
                                isLightTheme = isLightTheme
                            )
                        }
                    }
                }
            }

            // Glass Compact Segmented Tab Controls
            // Glass Compact Segmented Tab Controls
            item {
                GlassCard(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Applications", "Network Hosts", "Traffic Stream").forEachIndexed { index, label ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) {
                                            Brush.verticalGradient(
                                                colors = if (isLightTheme) {
                                                    listOf(Color(0xCC007AFF), Color(0x990055BB))
                                                } else {
                                                    listOf(Color(0xCC0A84FF), Color(0x990066DD))
                                                }
                                            )
                                        } else {
                                            Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent))
                                        }
                                    )
                                    .clickable { selectedTab = index }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else (if (isLightTheme) Color.DarkGray.copy(alpha = 0.8f) else Color.LightGray.copy(alpha = 0.8f)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Tab contents
            if (selectedTab == 0) {
                if (blockedAppList.isEmpty()) {
                    item {
                        Text(
                            text = "No apps blocked yet.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    items(blockedAppList) { app ->
                        GlassCard(
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (app.second != null) {
                                        AppIconImage(drawable = app.second!!, modifier = Modifier.size(36.dp))
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = app.first.take(1),
                                                color = contentColor.copy(0.6f),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = app.first,
                                            color = contentColor,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Ad & Tracker Redirection Securing",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFFF3B30).copy(alpha = 0.12f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${app.third} blocks",
                                        color = Color(0xFFFF3B30),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (selectedTab == 1) {
                val recentBlocks = ShieldStatsManager.recentBlocks.toList()
                if (recentBlocks.isEmpty()) {
                    item {
                        Text(
                            text = "No connections blocked yet.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    items(recentBlocks) { entry ->
                        GlassCard(
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = entry.domain,
                                        color = contentColor,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Blocked request from ${entry.appName}",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                                val relativeTime = DateUtils.getRelativeTimeSpanString(
                                    entry.timestamp,
                                    System.currentTimeMillis(),
                                    DateUtils.SECOND_IN_MILLIS
                                ).toString()
                                Text(
                                    text = relativeTime,
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            } else {
                val recentTraffic = ShieldStatsManager.recentTraffic.toList()
                if (recentTraffic.isEmpty()) {
                    item {
                        Text(
                            text = "No traffic recorded yet.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    items(recentTraffic) { entry ->
                        GlassCard(
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = entry.domain,
                                        color = contentColor,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${if (entry.isBlocked) "Blocked" else "Allowed"} request from ${entry.appName}",
                                        color = if (entry.isBlocked) Color(0xFFFF3B30) else Color(0xFF34C759),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                val relativeTime = DateUtils.getRelativeTimeSpanString(
                                    entry.timestamp,
                                    System.currentTimeMillis(),
                                    DateUtils.SECOND_IN_MILLIS
                                ).toString()
                                Text(
                                    text = relativeTime,
                                    color = Color.Gray,
                                    fontSize = 11.sp
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
fun SegmentTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightTheme = LocalIsLightTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    
    val targetBg = if (selected) {
        if (isLightTheme) Color.White else Color(0xFF2C2C2E)
    } else {
        Color.Transparent
    }
    val targetContentColor = if (selected) {
        if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF)
    } else {
        Color.Gray
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(targetBg)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = targetContentColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun LiquidGlassGraph(
    history: List<Pair<Long, Int>>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFFFF3B30),
    fillColor: Color = Color(0xFFFF3B30).copy(alpha = 0.15f)
) {
    Canvas(modifier = modifier) {
        if (history.size < 2) return@Canvas
        
        val width = size.width
        val height = size.height
        
        val maxVal = history.maxOf { it.second }.coerceAtLeast(1)
        val minVal = 0
        val range = maxVal - minVal
        
        val points = history.mapIndexed { idx, pair ->
            val x = idx * (width / (history.size - 1))
            val y = height - ((pair.second - minVal).toFloat() / range * (height * 0.7f) + (height * 0.15f))
            Offset(x, y)
        }
        
        // Bezier Line Path
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val cp1x = p0.x + (p1.x - p0.x) / 2f
                val cp1y = p0.y
                val cp2x = p0.x + (p1.x - p0.x) / 2f
                val cp2y = p1.y
                cubicTo(cp1x, cp1y, cp2x, cp2y, p1.x, p1.y)
            }
        }
        
        // Gradient Fill Path
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        
        // Draw Glossy Glassy Fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent)
            )
        )
        
        // Draw Bezier Line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Draw Node Dots
        points.forEach { pt ->
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = Color.White,
                radius = 1.5.dp.toPx(),
                center = pt
            )
        }
    }
}

@Composable
fun DailyBarChart(
    dailyHistory: Map<String, Int>,
    modifier: Modifier = Modifier,
    isLightTheme: Boolean
) {
    val sortedEntries = remember(dailyHistory) {
        dailyHistory.entries.sortedBy { it.key }.takeLast(7)
    }

    val maxVal = remember(sortedEntries) {
        sortedEntries.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        sortedEntries.forEach { entry ->
            val dayName = remember(entry.key) {
                try {
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(entry.key)
                    if (date != null) {
                        java.text.SimpleDateFormat("EEE", java.util.Locale.US).format(date)
                    } else {
                        entry.key.takeLast(2)
                    }
                } catch (e: Exception) {
                    entry.key.takeLast(2)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${entry.value}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLightTheme) Color.DarkGray else Color.LightGray
                )

                // The bar capsule
                val fillHeightRatio = entry.value.toFloat() / maxVal
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(100.dp * fillHeightRatio.coerceAtLeast(0.08f))
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = if (isLightTheme) {
                                    listOf(Color(0xCC007AFF), Color(0x6600C7BE))
                                } else {
                                    listOf(Color(0xCC0A84FF), Color(0x6664D2FF))
                                }
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = if (isLightTheme) 0.45f else 0.18f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.TopStart
                ) {
                    // Glossy highlight overlay reflection inside cylinder
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(3.dp)
                            .padding(top = 2.dp, start = 2.dp, bottom = 2.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                }

                Text(
                    text = dayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun CategoryPieChart(
    categoryCounts: Map<String, Int>,
    modifier: Modifier = Modifier,
    isLightTheme: Boolean
) {
    val total = remember(categoryCounts) {
        categoryCounts.values.sum().coerceAtLeast(1)
    }

    val categories = remember(categoryCounts) {
        categoryCounts.entries.sortedByDescending { it.value }
    }

    val categoryColors = remember {
        mapOf(
            "AD" to Color(0xFFFF453A),         // Neon Red
            "TRACKER" to Color(0xFFFF9F0A),    // Neon Orange
            "ANALYTICS" to Color(0xFF64D2FF),  // Neon Blue/Cyan
            "MALWARE" to Color(0xBFBF5AF2),    // Neon Purple
            "OTHER" to Color(0xFF30D158)        // Neon Green
        )
    }

    val contentColor = if (isLightTheme) Color.Black else Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(100.dp)) {
                var startAngle = -90f
                categories.forEach { entry ->
                    val sweepAngle = (entry.value.toFloat() / total) * 360f
                    val color = categoryColors[entry.key.uppercase()] ?: Color.Gray
                    
                    // Main liquid glass arc
                    drawArc(
                        color = color.copy(alpha = 0.8f),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    )
                    
                    // Glossy highlights reflection arc (inset reflection)
                    if (sweepAngle > 4f) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.45f),
                            startAngle = startAngle + 2f,
                            sweepAngle = sweepAngle - 4f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    
                    startAngle += sweepAngle
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$total",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = "Total",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            categories.take(5).forEach { entry ->
                val color = categoryColors[entry.key.uppercase()] ?: Color.Gray
                val percentage = (entry.value.toFloat() / total * 100).toInt()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Text(
                        text = "${entry.key.lowercase().replaceFirstChar { it.uppercase() }}: $percentage%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
