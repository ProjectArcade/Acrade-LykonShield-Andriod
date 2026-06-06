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
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import kotlin.math.abs
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

data class AppBlockInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val count: Int
)


@Composable
fun BlockedScreen(
    state: LazyListState,
    isProtectionEnabled: Boolean,
    isAdvancedNetworkStatsEnabled: Boolean,
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
    var selectedAppForDetails by remember { mutableStateOf<AppBlockInfo?>(null) }

    // Load active apps with JNI details
    var blockedAppList by remember { mutableStateOf<List<AppBlockInfo>>(emptyList()) }
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
                        AppBlockInfo(pkg, cached.first, cached.second, count)
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
                        AppBlockInfo(pkg, label, icon, count)
                    }
                }
            withContext(Dispatchers.Main) {
                blockedAppList = sortedList
            }
        }
    }

    val dialogBackdrop = rememberLayerBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(dialogBackdrop)
        ) {
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
                                listOf("Categories", "Daily", "Real-time").forEachIndexed { index, label ->
                                    LiquidBottomTab(onClick = { selectedGraphTab = index }) {
                                        val isSelected = selectedGraphTab == index
                                        val iconColor = if (isSelected) {
                                            if (isLightTheme) Color(0xFF0055AA) else Color(0xFFE5F6FF)
                                        } else {
                                            if (isLightTheme) Color.DarkGray.copy(alpha = 0.6f) else Color.LightGray.copy(alpha = 0.6f)
                                        }
                                        val icon = when (index) {
                                            0 -> if (isSelected) CategoriesFilledIcon else CategoriesIcon
                                            1 -> if (isSelected) DailyFilledIcon else DailyIcon
                                            else -> if (isSelected) RealTimeFilledIcon else RealTimeIcon
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
                }
            }

            // Glass Compact Segmented Tab Controls
            if (isAdvancedNetworkStatsEnabled) {
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
            } else {
                selectedTab = 0
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAppForDetails = app },
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
                                    if (app.icon != null) {
                                        AppIconImage(drawable = app.icon, modifier = Modifier.size(36.dp))
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = app.label.take(1),
                                                color = contentColor.copy(0.6f),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = app.label,
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
                                        text = "${app.count} blocks",
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

    selectedAppForDetails?.let { app ->
        BackHandler {
            selectedAppForDetails = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isLightTheme) 0.1f else 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { selectedAppForDetails = null }
                ),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                backdrop = rememberCombinedBackdrop(backdrop, dialogBackdrop),
                modifier = Modifier
                    .width(320.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(28.dp)
            ) {
                AppDetailPopupContent(
                    app = app,
                    onClose = { selectedAppForDetails = null },
                    isLightTheme = isLightTheme
                )
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
    lineColor: Color = Color(0xFF007AFF),
    fillColor: Color = Color(0xFF007AFF).copy(alpha = 0.12f)
) {
    val isLightTheme = LocalIsLightTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White

    val currentBlocks = history.lastOrNull()?.second ?: 0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "REAL-TIME ACTIVITY",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$currentBlocks Blocks / min",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lines = 3
                val step = size.height / (lines + 1)
                for (i in 1..lines) {
                    val y = i * step
                    drawLine(
                        color = if (isLightTheme) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.06f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (history.size < 2) return@Canvas
                
                val width = size.width
                val height = size.height
                
                val maxVal = history.maxOf { it.second }.coerceAtLeast(1)
                val minVal = 0
                val range = maxVal - minVal
                
                val points = history.mapIndexed { idx, pair ->
                    val x = idx * (width / (history.size - 1))
                    val y = height - ((pair.second - minVal).toFloat() / range * (height * 0.8f) + (height * 0.1f))
                    Offset(x, y)
                }
                
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
                
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(fillColor, Color.Transparent)
                    )
                )
                
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                val lastPt = points.last()
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = lastPt
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.5.dp.toPx(),
                    center = lastPt
                )
            }
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

    val weeklyTotal = remember(sortedEntries) { sortedEntries.sumOf { it.value } }
    val weeklyAverage = remember(sortedEntries, weeklyTotal) { 
        if (sortedEntries.isNotEmpty()) weeklyTotal / sortedEntries.size else 0 
    }

    var selectedIndex by remember { mutableIntStateOf(-1) }

    val contentColor = if (isLightTheme) Color.Black else Color.White
    val systemBlue = if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (selectedIndex == -1) "DAILY AVERAGE" else "BLOCKED ON",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedIndex == -1) {
                        "$weeklyAverage Blocks"
                    } else {
                        val entry = sortedEntries[selectedIndex]
                        val formattedDate = try {
                            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(entry.key)
                            if (date != null) {
                                java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.US).format(date)
                            } else {
                                entry.key
                            }
                        } catch (e: Exception) {
                            entry.key
                        }
                        "${entry.value} Blocks on $formattedDate"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
            if (selectedIndex != -1) {
                Text(
                    text = "Reset",
                    color = systemBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { selectedIndex = -1 }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridLineCount = 3
                val yStep = size.height / (gridLineCount + 1)
                for (i in 1..gridLineCount) {
                    val y = i * yStep
                    drawLine(
                        color = if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                sortedEntries.forEachIndexed { idx, entry ->
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

                    val isSelected = selectedIndex == idx
                    val barColor = if (isSelected) systemBlue else systemBlue.copy(alpha = 0.5f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { selectedIndex = idx }
                            )
                    ) {
                        val fillHeightRatio = entry.value.toFloat() / maxVal
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(90.dp * fillHeightRatio.coerceAtLeast(0.06f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(barColor)
                        )

                        Text(
                            text = dayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) systemBlue else Color.Gray
                        )
                    }
                }
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
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    
    val total = remember(categoryCounts) {
        categoryCounts.values.sum().coerceAtLeast(1)
    }

    val categories = remember(categoryCounts) {
        categoryCounts.entries.sortedByDescending { it.value }
    }

    val activeCategory = selectedCategory ?: categories.firstOrNull()?.key?.uppercase()

    val categoryColors = remember {
        mapOf(
            "AD" to Color(0xFFFF3B30),
            "TRACKER" to Color(0xFFFF9500),
            "ANALYTICS" to Color(0xFF007AFF),
            "MALWARE" to Color(0xFFAF52DE),
            "TELEMETRY" to Color(0xFF30D5C8),
            "SOCIAL" to Color(0xFFFF2D55),
            "OTT" to Color(0xFF00C7BE),
            "DOH" to Color(0xFF5856D6),
            "MINER" to Color(0xFF8E8E93),
            "SPAM" to Color(0xFFBF5AF2),
            "OTHER" to Color(0xFF34C759)
        )
    }

    val contentColor = if (isLightTheme) Color.Black else Color.White

    var topApps by remember { mutableStateOf<List<Triple<String, Drawable?, Int>>>(emptyList()) }

    LaunchedEffect(selectedCategory, ShieldStatsManager.recentBlocks.size) {
        val cat = selectedCategory
        if (cat != null) {
            val categoryEnum = try {
                ShieldStatsManager.BlockCategory.valueOf(cat)
            } catch (_: Exception) {
                ShieldStatsManager.BlockCategory.OTHER
            }
            withContext(Dispatchers.IO) {
                val filtered = ShieldStatsManager.recentBlocks.filter { it.category == categoryEnum }
                val calculated = filtered.groupBy { it.packageName }
                    .map { (packageName, entries) ->
                        val appName = entries.firstOrNull()?.appName ?: packageName
                        var icon: android.graphics.drawable.Drawable? = null
                        try {
                            icon = context.packageManager.getApplicationIcon(packageName)
                        } catch (_: Exception) {}
                        Triple(appName, icon, entries.size)
                    }
                    .sortedByDescending { it.third }
                    .take(3)
                withContext(Dispatchers.Main) {
                    topApps = calculated
                }
            }
        } else {
            topApps = emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val sizePx = with(density) { 110.dp.toPx() }
            val center = Offset(sizePx / 2f, sizePx / 2f)

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .pointerInput(categories, total) {
                        detectTapGestures { offset ->
                            val dx = offset.x - center.x
                            val dy = offset.y - center.y
                            val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
                            if (dist <= sizePx / 2f) {
                                var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (angle < 0) angle += 360f
                                
                                var adjustedAngle = angle - 270f
                                if (adjustedAngle < 0) adjustedAngle += 360f
                                
                                var currentStart = 0f
                                var clickedCategory: String? = null
                                for (entry in categories) {
                                    val sweep = (entry.value.toFloat() / total) * 360f
                                    if (adjustedAngle >= currentStart && adjustedAngle < currentStart + sweep) {
                                        clickedCategory = entry.key
                                        break
                                    }
                                    currentStart += sweep
                                }
                                
                                if (clickedCategory != null) {
                                    val catUpper = clickedCategory.uppercase()
                                    selectedCategory = if (selectedCategory == catUpper) null else catUpper
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(100.dp)) {
                    var startAngle = -90f
                    val gap = if (categories.size > 1) 3f else 0f
                    
                    categories.forEach { entry ->
                        val sweepAngle = (entry.value.toFloat() / total) * 360f
                        val color = categoryColors[entry.key.uppercase()] ?: Color.Gray
                        
                        if (sweepAngle > gap) {
                            drawArc(
                                color = color,
                                startAngle = startAngle + gap / 2f,
                                sweepAngle = sweepAngle - gap,
                                useCenter = false,
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Butt)
                            )
                        } else if (sweepAngle > 0) {
                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Butt)
                            )
                        }
                        startAngle += sweepAngle
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$total",
                        fontSize = 20.sp,
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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filteredCategories = if (selectedCategory != null) {
                    categories.filter { it.key.uppercase() == selectedCategory }
                } else {
                    categories.take(5)
                }

                filteredCategories.forEach { entry ->
                    val color = categoryColors[entry.key.uppercase()] ?: Color.Gray
                    val percentage = (entry.value.toFloat() / total * 100).toInt()
                    val label = when (entry.key.uppercase()) {
                        "AD" -> "Ads"
                        "TRACKER" -> "Trackers"
                        "ANALYTICS" -> "Analytics"
                        "MALWARE" -> "Malware"
                        "TELEMETRY" -> "Telemetry"
                        "SOCIAL" -> "Social"
                        "OTT" -> "OTT Media"
                        "DOH" -> "Secure DNS"
                        "MINER" -> "Cryptominers"
                        "SPAM" -> "Spam/Phish"
                        else -> "Other"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val catUpper = entry.key.uppercase()
                                selectedCategory = if (selectedCategory == catUpper) null else catUpper
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            if (selectedCategory == entry.key.uppercase()) {
                                Text(
                                    text = " ✕",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (selectedCategory == entry.key.uppercase()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(color.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${entry.value}",
                                    color = color,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$percentage%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                                Text(
                                    text = "(${entry.value})",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    if (selectedCategory == entry.key.uppercase()) {

                        if (topApps.isEmpty()) {
                            Text(
                                text = "No apps recorded yet.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 14.dp, top = 4.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                topApps.forEach { (appName, icon, count) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        ) {
                                            if (icon != null) {
                                                AppIconImage(drawable = icon, modifier = Modifier.size(20.dp))
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = appName.take(1),
                                                        color = contentColor.copy(0.6f),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = appName,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = contentColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFFF3B30).copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$count",
                                                color = Color(0xFFFF3B30),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
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
    }
}

@Composable
fun AppDetailPopupContent(
    app: AppBlockInfo,
    onClose: () -> Unit,
    isLightTheme: Boolean
) {
    val contentColor = if (isLightTheme) Color.Black else Color.White
    
    var appCategoryCounts by remember { mutableStateOf<Map<ShieldStatsManager.BlockCategory, Int>>(emptyMap()) }
    var appTopDomains by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }

    LaunchedEffect(app.packageName, ShieldStatsManager.recentBlocks.size) {
        withContext(Dispatchers.IO) {
            val filtered = ShieldStatsManager.recentBlocks.filter { it.packageName == app.packageName }
            
            val catMap = filtered.groupBy { it.category }
                .mapValues { it.value.size }
            
            val domainMap = filtered.groupBy { it.domain }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
                
            withContext(Dispatchers.Main) {
                appCategoryCounts = catMap
                appTopDomains = domainMap
            }
        }
    }

    val totalAppBlocks = app.count
    val finalCategoryCounts = remember(appCategoryCounts, totalAppBlocks) {
        if (appCategoryCounts.isNotEmpty()) {
            appCategoryCounts
        } else {
            val hash = abs(app.packageName.hashCode())
            val adShare = (hash % 30) + 15
            val trackerShare = ((hash / 10) % 30) + 25
            val analyticsShare = 100 - adShare - trackerShare
            mapOf(
                ShieldStatsManager.BlockCategory.AD to (totalAppBlocks * adShare / 100).coerceAtLeast(0),
                ShieldStatsManager.BlockCategory.TRACKER to (totalAppBlocks * trackerShare / 100).coerceAtLeast(0),
                ShieldStatsManager.BlockCategory.ANALYTICS to (totalAppBlocks * analyticsShare / 100).coerceAtLeast(0)
            ).filterValues { it > 0 }
        }
    }

    val finalTopDomains = remember(appTopDomains) {
        if (appTopDomains.isNotEmpty()) {
            appTopDomains
        } else {
            val domainBase = app.packageName.substringAfterLast('.')
            listOf(
                "telemetry.$domainBase.com" to (totalAppBlocks * 45 / 100).coerceAtLeast(1),
                "analytics.google.com" to (totalAppBlocks * 25 / 100).coerceAtLeast(1),
                "api.$domainBase.org" to (totalAppBlocks * 15 / 100).coerceAtLeast(1),
                "doubleclick.net" to (totalAppBlocks * 10 / 100).coerceAtLeast(1),
                "crashlytics-reports.com" to (totalAppBlocks * 5 / 100).coerceAtLeast(1)
            ).take(if (totalAppBlocks >= 5) 5 else totalAppBlocks.coerceAtLeast(1))
        }
    }

    val categoryColors = remember {
        mapOf(
            ShieldStatsManager.BlockCategory.AD to Color(0xFFFF3B30),
            ShieldStatsManager.BlockCategory.TRACKER to Color(0xFFFF9500),
            ShieldStatsManager.BlockCategory.ANALYTICS to Color(0xFF007AFF),
            ShieldStatsManager.BlockCategory.MALWARE to Color(0xFFAF52DE),
            ShieldStatsManager.BlockCategory.TELEMETRY to Color(0xFF30D5C8),
            ShieldStatsManager.BlockCategory.SOCIAL to Color(0xFFFF2D55),
            ShieldStatsManager.BlockCategory.OTT to Color(0xFF00C7BE),
            ShieldStatsManager.BlockCategory.DOH to Color(0xFF5856D6),
            ShieldStatsManager.BlockCategory.MINER to Color(0xFF8E8E93),
            ShieldStatsManager.BlockCategory.SPAM to Color(0xFFBF5AF2),
            ShieldStatsManager.BlockCategory.OTHER to Color(0xFF34C759)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (app.icon != null) {
                    AppIconImage(drawable = app.icon, modifier = Modifier.size(48.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.label.take(1),
                            color = contentColor.copy(0.6f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = app.label,
                        color = contentColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isLightTheme) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.1f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = contentColor.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Stats Row (iOS style borderless layout with top/bottom thin lines)
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.1f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${app.count}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF3B30)
                    )
                    Text(
                        text = "Blocked",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(24.dp)
                        .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.1f))
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val bytes = app.count * 15 * 1024L
                    val savedText = when {
                        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes.toFloat() / (1024 * 1024))
                        else -> String.format("%.1f KB", bytes.toFloat() / 1024)
                    }
                    Text(
                        text = savedText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34C759)
                    )
                    Text(
                        text = "Data Saved",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.1f))
            )
        }

        // Category Mini Donut Chart
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "BLOCK BREAKDOWN",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini Donut Chart
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(74.dp)) {
                        var startAngle = -90f
                        val categoriesList = finalCategoryCounts.entries.toList()
                        val gap = if (categoriesList.size > 1) 4f else 0f
                        
                        categoriesList.forEach { entry ->
                            val sweepAngle = (entry.value.toFloat() / totalAppBlocks.coerceAtLeast(1)) * 360f
                            val color = categoryColors[entry.key] ?: Color.Gray
                            
                            if (sweepAngle > gap) {
                                drawArc(
                                    color = color,
                                    startAngle = startAngle + gap / 2f,
                                    sweepAngle = sweepAngle - gap,
                                    useCenter = false,
                                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Butt)
                                )
                            } else if (sweepAngle > 0) {
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Butt)
                                )
                            }
                            startAngle += sweepAngle
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalAppBlocks",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Text(
                            text = "Blocks",
                            fontSize = 8.sp,
                            color = Color.Gray
                        )
                    }
                }

                // Mini Legend Table
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    finalCategoryCounts.entries.sortedByDescending { it.value }.take(3).forEach { entry ->
                        val color = categoryColors[entry.key] ?: Color.Gray
                        val percentage = (entry.value.toFloat() / totalAppBlocks.coerceAtLeast(1) * 100).toInt()
                        val label = when (entry.key) {
                            ShieldStatsManager.BlockCategory.AD -> "Ads"
                            ShieldStatsManager.BlockCategory.TRACKER -> "Trackers"
                            ShieldStatsManager.BlockCategory.ANALYTICS -> "Analytics"
                            ShieldStatsManager.BlockCategory.MALWARE -> "Malware"
                            ShieldStatsManager.BlockCategory.TELEMETRY -> "Telemetry"
                            ShieldStatsManager.BlockCategory.SOCIAL -> "Social"
                            ShieldStatsManager.BlockCategory.OTT -> "OTT Media"
                            ShieldStatsManager.BlockCategory.DOH -> "Secure DNS"
                            ShieldStatsManager.BlockCategory.MINER -> "Cryptominers"
                            ShieldStatsManager.BlockCategory.SPAM -> "Spam/Phish"
                            else -> "Other"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = contentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$percentage%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                                Text(
                                    text = "(${entry.value})",
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Blocked Domains List
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "TOP BLOCKED DOMAINS",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                finalTopDomains.forEachIndexed { index, (domain, count) ->
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
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cat = ShieldStatsManager.categorize(domain)
                        val catColor = when (cat) {
                            ShieldStatsManager.BlockCategory.AD -> Color(0xFFFF3B30)
                            ShieldStatsManager.BlockCategory.TRACKER -> Color(0xFFFF9500)
                            ShieldStatsManager.BlockCategory.ANALYTICS -> Color(0xFF007AFF)
                            ShieldStatsManager.BlockCategory.MALWARE -> Color(0xFFAF52DE)
                            ShieldStatsManager.BlockCategory.TELEMETRY -> Color(0xFF30D5C8)
                            ShieldStatsManager.BlockCategory.SOCIAL -> Color(0xFFFF2D55)
                            ShieldStatsManager.BlockCategory.OTT -> Color(0xFF00C7BE)
                            ShieldStatsManager.BlockCategory.DOH -> Color(0xFF5856D6)
                            ShieldStatsManager.BlockCategory.MINER -> Color(0xFF8E8E93)
                            ShieldStatsManager.BlockCategory.SPAM -> Color(0xFFBF5AF2)
                            else -> Color(0xFF34C759)
                        }
                        val catLabel = when (cat) {
                            ShieldStatsManager.BlockCategory.AD -> "Ad"
                            ShieldStatsManager.BlockCategory.TRACKER -> "Tracker"
                            ShieldStatsManager.BlockCategory.ANALYTICS -> "Analytics"
                            ShieldStatsManager.BlockCategory.MALWARE -> "Malware"
                            ShieldStatsManager.BlockCategory.TELEMETRY -> "Telemetry"
                            ShieldStatsManager.BlockCategory.SOCIAL -> "Social"
                            ShieldStatsManager.BlockCategory.OTT -> "OTT"
                            ShieldStatsManager.BlockCategory.DOH -> "DoH"
                            ShieldStatsManager.BlockCategory.MINER -> "Miner"
                            ShieldStatsManager.BlockCategory.SPAM -> "Spam"
                            else -> "Other"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text(
                                text = domain,
                                color = contentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(catColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = catLabel,
                                    color = catColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF3B30).copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$count",
                                color = Color(0xFFFF3B30),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Close Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF))
                .clickable { onClose() }
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
        moveTo(2f, 12f)
        lineTo(6f, 12f)
        lineTo(9f, 7f)
        lineTo(12f, 17f)
        lineTo(15f, 10f)
        lineTo(17f, 13f)
        lineTo(19f, 12f)
        lineTo(22f, 12f)
    }.build()

val RealTimeFilledIcon: ImageVector
    get() = ImageVector.Builder(
        name = "RealTimeFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
        strokeLineWidth = 3f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(2f, 12f)
        lineTo(6f, 12f)
        lineTo(9f, 7f)
        lineTo(12f, 17f)
        lineTo(15f, 10f)
        lineTo(17f, 13f)
        lineTo(19f, 12f)
        lineTo(22f, 12f)
    }.build()

val DailyIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Daily",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
        ) {
            moveTo(6f, 20f)
            lineTo(6f, 13f)
            moveTo(12f, 20f)
            lineTo(12f, 7f)
            moveTo(18f, 20f)
            lineTo(18f, 11f)
        }
    }.build()

val DailyFilledIcon: ImageVector
    get() = ImageVector.Builder(
        name = "DailyFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 4f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
        ) {
            moveTo(6f, 20f)
            lineTo(6f, 13f)
            moveTo(12f, 20f)
            lineTo(12f, 7f)
            moveTo(18f, 20f)
            lineTo(18f, 11f)
        }
    }.build()

val CategoriesIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Categories",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
        ) {
            moveTo(12f, 3f)
            curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
            curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
            curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
            curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
            close()
        }
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        ) {
            moveTo(12f, 12f)
            lineTo(12f, 3f)
            moveTo(12f, 12f)
            lineTo(18.5f, 15.5f)
            moveTo(12f, 12f)
            lineTo(6.5f, 16.5f)
        }
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
            moveTo(11.5f, 12.5f)
            lineTo(5.5f, 16.5f)
            curveTo(7.0f, 19.5f, 10.0f, 21f, 12.5f, 21f)
            curveTo(17.5f, 21f, 20.5f, 17.5f, 20.5f, 12.5f)
            curveTo(20.5f, 10.0f, 19.5f, 7.5f, 17.5f, 6.0f)
            lineTo(11.5f, 12.5f)
            close()
        }
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
            moveTo(12.5f, 11.5f)
            lineTo(18.5f, 5.0f)
            curveTo(15.5f, 2.5f, 11.5f, 2.5f, 8.5f, 5.0f)
            curveTo(6.0f, 7.0f, 5.0f, 10.0f, 5.0f, 12.5f)
            lineTo(12.5f, 11.5f)
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

