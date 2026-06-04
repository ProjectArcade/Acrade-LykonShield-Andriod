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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path


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
    var selectedGraphTab by remember { mutableStateOf(0) } // 0 = Real-time, 1 = Daily, 2 = Categories

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
            // Glass Dashboard Graphs Card with Tab View
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PROTECTION STATISTICS",
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
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Graph Switcher Tabs (Draggable Liquid Glass Tab View)
                            LiquidBottomTabs(
                                selectedTabIndex = { selectedGraphTab },
                                onTabSelected = { selectedGraphTab = it },
                                backdrop = backdrop,
                                tabsCount = 3,
                                accentColor = if (isLightTheme) Color(0xFF007AFF).copy(alpha = 0.8f) else Color(0xFF64D2FF),
                                height = 44.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("Real-time", "Daily", "Categories").forEachIndexed { index, label ->
                                    LiquidBottomTab(onClick = { selectedGraphTab = index }) {
                                        val isSelected = selectedGraphTab == index
                                        val iconColor = if (isSelected) {
                                            if (isLightTheme) Color(0xFF0055AA) else Color(0xFFE5F6FF)
                                        } else {
                                            if (isLightTheme) Color.DarkGray.copy(alpha = 0.6f) else Color.LightGray.copy(alpha = 0.6f)
                                        }
                                        val icon = when (index) {
                                            0 -> if (isSelected) RealTimeFilledIcon else RealTimeIcon
                                            1 -> if (isSelected) DailyFilledIcon else DailyIcon
                                            else -> if (isSelected) CategoriesFilledIcon else CategoriesIcon
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = iconColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = label,
                                            color = iconColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            // Active graph content showing only real data
                            when (selectedGraphTab) {
                                0 -> {
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
                                1 -> {
                                    val dailyHistory = ShieldStatsManager.dailyBlockHistory.toMap()
                                    if (dailyHistory.isNotEmpty()) {
                                        DailyBarChart(
                                            dailyHistory = dailyHistory,
                                            isLightTheme = isLightTheme
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(120.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No daily block activity recorded yet.", color = Color.Gray, fontSize = 13.sp)
                                        }
                                    }
                                }
                                2 -> {
                                    val categoryCounts = ShieldStatsManager.categoryBlockCounts.toMap()
                                    if (categoryCounts.values.any { it > 0 }) {
                                        CategoryPieChart(
                                            categoryCounts = categoryCounts,
                                            isLightTheme = isLightTheme
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(120.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No category classification logs yet.", color = Color.Gray, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Glass Compact Segmented Tab Controls
            item {
                LiquidBottomTabs(
                    selectedTabIndex = { selectedTab },
                    onTabSelected = { selectedTab = it },
                    backdrop = backdrop,
                    tabsCount = 3,
                    accentColor = if (isLightTheme) Color(0xFF007AFF).copy(alpha = 0.8f) else Color(0xFF64D2FF),
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Applications", "Network Hosts", "Traffic Stream").forEachIndexed { index, label ->
                        LiquidBottomTab(onClick = { selectedTab = index }) {
                            val isSelected = selectedTab == index
                            val iconColor = if (isSelected) {
                                if (isLightTheme) Color(0xFF0055AA) else Color(0xFFE5F6FF)
                            } else {
                                if (isLightTheme) Color.DarkGray.copy(alpha = 0.6f) else Color.LightGray.copy(alpha = 0.6f)
                            }
                            val icon = when (index) {
                                0 -> if (isSelected) AppsFilledIcon else AppsIcon
                                1 -> if (isSelected) NetworkFilledIcon else NetworkIcon
                                else -> if (isSelected) TrafficFilledIcon else TrafficIcon
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = label,
                                color = iconColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
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

// Custom Icons for Statistics & Tabs
val RealTimeIcon: ImageVector
    get() = ImageVector.Builder(
        name = "RealTime",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(3f, 16f)
        lineTo(9f, 10f)
        lineTo(14f, 14f)
        lineTo(21f, 6f)
    }.build()

val RealTimeFilledIcon: ImageVector
    get() = ImageVector.Builder(
        name = "RealTimeFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 2.5f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        ) {
            moveTo(3f, 16f)
            lineTo(9f, 10f)
            lineTo(14f, 14f)
            lineTo(21f, 6f)
        }
        path(
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
            fillAlpha = 0.25f
        ) {
            moveTo(3f, 16f)
            lineTo(9f, 10f)
            lineTo(14f, 14f)
            lineTo(21f, 6f)
            lineTo(21f, 20f)
            lineTo(3f, 20f)
            close()
        }
    }.build()

val DailyIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Daily",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        // Bar 1
        moveTo(4f, 20f)
        lineTo(4f, 12f)
        lineTo(8f, 12f)
        lineTo(8f, 20f)
        
        // Bar 2
        moveTo(10f, 20f)
        lineTo(10f, 6f)
        lineTo(14f, 6f)
        lineTo(14f, 20f)
        
        // Bar 3
        moveTo(16f, 20f)
        lineTo(16f, 10f)
        lineTo(20f, 10f)
        lineTo(20f, 20f)
    }.build()

val DailyFilledIcon: ImageVector
    get() = ImageVector.Builder(
        name = "DailyFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
    ) {
        // Bar 1
        moveTo(4f, 11f)
        curveTo(4f, 10.5f, 4.5f, 10f, 5f, 10f)
        horizontalLineTo(7f)
        curveTo(7.5f, 10f, 8f, 10.5f, 8f, 11f)
        verticalLineTo(20f)
        horizontalLineTo(4f)
        close()
        
        // Bar 2
        moveTo(10f, 5f)
        curveTo(10f, 4.5f, 10.5f, 4f, 11f, 4f)
        horizontalLineTo(13f)
        curveTo(13.5f, 4f, 14f, 4.5f, 14f, 5f)
        verticalLineTo(20f)
        horizontalLineTo(10f)
        close()
        
        // Bar 3
        moveTo(16f, 9f)
        curveTo(16f, 8.5f, 16.5f, 8f, 17f, 8f)
        horizontalLineTo(19f)
        curveTo(19.5f, 8f, 20f, 8.5f, 20f, 9f)
        verticalLineTo(20f)
        horizontalLineTo(16f)
        close()
    }.build()

val CategoriesIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Categories",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
        strokeLineWidth = 2f
    ) {
        moveTo(12f, 4f)
        curveTo(16.4f, 4f, 20f, 7.6f, 20f, 12f)
        curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
        curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
        curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
        close()
        moveTo(12f, 12f)
        lineTo(12f, 4f)
        moveTo(12f, 12f)
        lineTo(18f, 16f)
    }.build()

val CategoriesFilledIcon: ImageVector
    get() = ImageVector.Builder(
        name = "CategoriesFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
            moveTo(12f, 12f)
            lineTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 11f, 21.8f, 10f, 21.5f, 9f)
            lineTo(12f, 12f)
            close()
        }
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
            moveTo(13.5f, 10.5f)
            lineTo(22f, 7.5f)
            curveTo(21f, 5f, 19f, 3f, 16.5f, 2f)
            close()
        }
    }.build()

val AppsIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Apps",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(4f, 4f)
        horizontalLineTo(9f)
        verticalLineTo(9f)
        horizontalLineTo(4f)
        close()
        
        moveTo(15f, 4f)
        horizontalLineTo(20f)
        verticalLineTo(9f)
        horizontalLineTo(15f)
        close()
        
        moveTo(4f, 15f)
        horizontalLineTo(9f)
        verticalLineTo(20f)
        horizontalLineTo(4f)
        close()
        
        moveTo(15f, 15f)
        horizontalLineTo(20f)
        verticalLineTo(20f)
        horizontalLineTo(15f)
        close()
    }.build()

val AppsFilledIcon: ImageVector
    get() = ImageVector.Builder(
        name = "AppsFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
    ) {
        moveTo(4f, 4f)
        curveTo(4f, 3.5f, 4.5f, 3f, 5f, 3f)
        horizontalLineTo(9f)
        curveTo(9.5f, 3f, 10f, 3.5f, 10f, 4f)
        verticalLineTo(8f)
        curveTo(10f, 8.5f, 9.5f, 9f, 9f, 9f)
        horizontalLineTo(5f)
        curveTo(4.5f, 9f, 4f, 8.5f, 4f, 8f)
        close()
        
        moveTo(14f, 4f)
        curveTo(14f, 3.5f, 14.5f, 3f, 15f, 3f)
        horizontalLineTo(19f)
        curveTo(19.5f, 3f, 20f, 3.5f, 20f, 4f)
        verticalLineTo(8f)
        curveTo(20f, 8.5f, 19.5f, 9f, 19f, 9f)
        horizontalLineTo(15f)
        curveTo(14.5f, 9f, 14f, 8.5f, 14f, 8f)
        close()
        
        moveTo(4f, 14f)
        curveTo(4f, 13.5f, 4.5f, 13f, 5f, 13f)
        horizontalLineTo(9f)
        curveTo(9.5f, 13f, 10f, 13.5f, 10f, 14f)
        verticalLineTo(18f)
        curveTo(10f, 18.5f, 9.5f, 19f, 9f, 19f)
        horizontalLineTo(5f)
        curveTo(4.5f, 19f, 4f, 18.5f, 4f, 18f)
        close()
        
        moveTo(14f, 14f)
        curveTo(14f, 13.5f, 14.5f, 13f, 15f, 13f)
        horizontalLineTo(19f)
        curveTo(19.5f, 13f, 20f, 13.5f, 20f, 14f)
        verticalLineTo(18f)
        curveTo(20f, 18.5f, 19.5f, 19f, 19f, 19f)
        horizontalLineTo(15f)
        curveTo(14.5f, 19f, 14f, 18.5f, 14f, 18f)
        close()
    }.build()

val NetworkIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Network",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(12f, 3f)
        curveTo(17f, 3f, 21f, 7f, 21f, 12f)
        curveTo(21f, 17f, 17f, 21f, 12f, 21f)
        curveTo(7f, 21f, 3f, 17f, 3f, 12f)
        curveTo(3f, 7f, 7f, 3f, 12f, 3f)
        close()
        moveTo(3f, 12f)
        lineTo(21f, 12f)
        moveTo(12f, 3f)
        curveTo(10f, 6f, 10f, 18f, 12f, 21f)
        curveTo(14f, 18f, 14f, 6f, 12f, 3f)
    }.build()

val NetworkFilledIcon: ImageVector
    get() = ImageVector.Builder(
        name = "NetworkFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(17.52f, 2f, 22f, 6.48f, 22f, 12f)
            curveTo(22f, 17.52f, 17.52f, 22f, 12f, 22f)
            curveTo(6.48f, 22f, 2f, 17.52f, 2f, 12f)
            curveTo(2f, 6.48f, 6.48f, 2f, 12f, 2f)
            close()
        }
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        ) {
            moveTo(3f, 12f)
            lineTo(21f, 12f)
            moveTo(5f, 8f)
            lineTo(19f, 8f)
            moveTo(5f, 16f)
            lineTo(19f, 16f)
            moveTo(12f, 2f)
            lineTo(12f, 22f)
        }
    }.build()

val TrafficIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Traffic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(7f, 4f)
        lineTo(7f, 20f)
        moveTo(7f, 4f)
        lineTo(3f, 8f)
        moveTo(7f, 4f)
        lineTo(11f, 8f)
        
        moveTo(17f, 20f)
        lineTo(17f, 4f)
        moveTo(17f, 20f)
        lineTo(13f, 16f)
        moveTo(17f, 20f)
        lineTo(21f, 16f)
    }.build()

val TrafficFilledIcon: ImageVector
    get() = ImageVector.Builder(
        name = "TrafficFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
            moveTo(6f, 4f)
            horizontalLineTo(8f)
            verticalLineTo(15f)
            horizontalLineTo(6f)
            close()
            moveTo(4f, 14f)
            lineTo(7f, 20f)
            lineTo(10f, 14f)
            close()
        }
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
            moveTo(16f, 9f)
            horizontalLineTo(18f)
            verticalLineTo(20f)
            horizontalLineTo(16f)
            close()
            moveTo(14f, 10f)
            lineTo(17f, 4f)
            lineTo(20f, 10f)
            close()
        }
    }.build()

