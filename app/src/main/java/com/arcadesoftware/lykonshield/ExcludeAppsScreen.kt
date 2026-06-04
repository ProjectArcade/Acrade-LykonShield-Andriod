package com.arcadesoftware.lykonshield

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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

// SF Symbol: magnifyingglass
private val SFSearchIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SFSearch",
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
        moveTo(17.5f, 10.5f)
        curveTo(17.5f, 14.37f, 14.37f, 17.5f, 10.5f, 17.5f)
        curveTo(6.63f, 17.5f, 3.5f, 14.37f, 3.5f, 10.5f)
        curveTo(3.5f, 6.63f, 6.63f, 3.5f, 10.5f, 3.5f)
        curveTo(14.37f, 3.5f, 17.5f, 6.63f, 17.5f, 10.5f)
        close()
        moveTo(15.5f, 15.5f)
        lineTo(20.5f, 20.5f)
    }.build()

// SF Symbol: xmark.circle.fill
private val SFXmarkCircleIcon: ImageVector
    get() = ImageVector.Builder(
        name = "SFXmarkCircle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        ) {
            moveTo(22f, 12f)
            curveTo(22f, 17.52f, 17.52f, 22f, 12f, 22f)
            curveTo(6.48f, 22f, 2f, 17.52f, 2f, 12f)
            curveTo(2f, 6.48f, 6.48f, 2f, 12f, 2f)
            curveTo(17.52f, 2f, 22f, 6.48f, 22f, 12f)
            close()
            moveTo(8.5f, 8f)
            lineTo(9f, 8f)
            lineTo(12f, 11f)
            lineTo(15f, 8f)
            lineTo(16f, 8f)
            lineTo(16f, 9f)
            lineTo(13f, 12f)
            lineTo(16f, 15f)
            lineTo(16f, 16f)
            lineTo(15f, 16f)
            lineTo(12f, 13f)
            lineTo(9f, 16f)
            lineTo(8f, 16f)
            lineTo(8f, 15f)
            lineTo(11f, 12f)
            lineTo(8f, 9f)
            lineTo(8f, 8f)
            close()
        }
    }.build()

@Composable
private fun SkeletonShimmerItem(isLightTheme: Boolean) {
    val shimmerColors = if (isLightTheme) {
        listOf(Color(0xFFE0E0E0), Color(0xFFF5F5F5), Color(0xFFE0E0E0))
    } else {
        listOf(Color(0xFF2C2C2E), Color(0xFF3A3A3C), Color(0xFF2C2C2E))
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 300f, 0f)
    )

    val cardBg = if (isLightTheme) Color.White else Color(0xFF1C1C1E)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(12.dp)
    ) {
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
fun ExcludeAppsScreen(
    navController: androidx.navigation.NavController,
    excludedApps: Set<String>,
    onToggleApp: (String) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val cardBg = if (isLightTheme) Color.White else Color(0xFF1C1C1E)
    val localBackdrop = rememberLayerBackdrop()
    val screenContentBackdrop = rememberLayerBackdrop()

    val context = LocalContext.current
    val pm = context.packageManager

    var appList by remember { mutableStateOf<List<Triple<String, String, android.graphics.drawable.Drawable?>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                val launcherPackages = pm.queryIntentActivities(launcherIntent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .toSet()

                val packages = pm.getInstalledPackages(0)
                val filtered = packages.mapNotNull { pkg ->
                    val appInfo = pkg.applicationInfo
                    if (appInfo != null) {
                        val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        if (!isSystem || launcherPackages.contains(pkg.packageName)) {
                            val name = appInfo.loadLabel(pm).toString()
                            val pkgName = pkg.packageName
                            val icon = appInfo.loadIcon(pm)
                            Triple(name, pkgName, icon)
                        } else null
                    } else null
                }.sortedBy { it.first.lowercase() }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    appList = filtered
                    isLoading = false
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val finalApps = remember(appList, searchQuery, isLoading) {
        val base = if (appList.isEmpty() && !isLoading) {
            listOf(
                Triple("Google Chrome", "com.android.chrome", null),
                Triple("YouTube", "com.google.android.youtube", null),
                Triple("Instagram", "com.instagram.android", null),
                Triple("WhatsApp", "com.whatsapp", null),
                Triple("Facebook", "com.facebook.katana", null),
                Triple("Spotify", "com.spotify.music", null),
                Triple("TikTok", "com.zhiliaoapp.musically", null),
                Triple("Netflix", "com.netflix.mediaclient", null),
                Triple("Gmail", "com.google.android.gm", null)
            )
        } else {
            appList
        }

        if (searchQuery.isEmpty()) {
            base
        } else {
            base.filter {
                it.first.contains(searchQuery, ignoreCase = true) ||
                it.second.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Close search on back press
    if (isSearchExpanded) {
        androidx.activity.compose.BackHandler {
            searchQuery = ""
            isSearchExpanded = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLightTheme) Color(0xFFF2F2F7) else Color.Black)
    ) {
        Box(
            modifier = Modifier
                .layerBackdrop(screenContentBackdrop)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topPadding + 64.dp,
                    bottom = bottomPadding + 80.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Bypass Apps",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                if (isLoading) {
                    items(8) {
                        SkeletonShimmerItem(isLightTheme = isLightTheme)
                    }
                } else if (finalApps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No apps found",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                    }
                } else {
                    items(finalApps) { app ->
                        val isBypassed = excludedApps.contains(app.second)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg)
                                .padding(12.dp)
                        ) {
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
                                    if (app.third != null) {
                                        val drawable = app.third!!
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
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = app.first,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF007AFF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = app.first.take(1),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = app.first,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            color = contentColor
                                        )
                                        Text(
                                            text = app.second,
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                LiquidToggle(
                                    selected = { isBypassed },
                                    onSelect = { onToggleApp(app.second) },
                                    backdrop = localBackdrop
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── Floating back button (top-left) ────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp + topPadding)
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

        // ─── Expanded search bar (bottom) — iOS 17 spring animation ─────────────
        AnimatedVisibility(
            visible = isSearchExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 350f)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f)
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomPadding + 16.dp)
        ) {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBackdrop(
                        backdrop = screenContentBackdrop,
                        shape = { CircleShape },
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
                    .height(48.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = SFSearchIcon,
                        contentDescription = "Search",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Text field — left-aligned (CenterStart), not centered
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = contentColor,
                            fontSize = 16.sp
                        ),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(if (isLightTheme) Color.Black else Color.White),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search apps...",
                                        color = Color.Gray,
                                        fontSize = 16.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = SFXmarkCircleIcon,
                            contentDescription = "Clear",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Cancel",
                        color = if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            searchQuery = ""
                            isSearchExpanded = false
                        }
                    )
                }
            }
        }

        // ─── FAB search button (bottom-right) — same style as back button ───────
        AnimatedVisibility(
            visible = !isSearchExpanded,
            enter = scaleIn(
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
            ) + fadeIn(tween(200)),
            exit = scaleOut(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)
            ) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomPadding + 20.dp)
        ) {
            LiquidButton(
                onClick = { isSearchExpanded = true },
                backdrop = screenContentBackdrop,
                modifier = Modifier.size(48.dp),
                shape = { CircleShape },
                surfaceColor = Color.Transparent
            ) {
                Icon(
                    imageVector = SFSearchIcon,
                    contentDescription = "Search",
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
