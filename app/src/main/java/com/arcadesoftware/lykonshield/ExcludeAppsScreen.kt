package com.arcadesoftware.lykonshield

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
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
                val packages = pm.getInstalledPackages(0)
                val filtered = packages.mapNotNull { pkg ->
                    val appInfo = pkg.applicationInfo
                    if (appInfo != null) {
                        val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        if (!isSystem) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLightTheme) Color(0xFFF2F2F7) else Color.Black)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .layerBackdrop(screenContentBackdrop)
                    .fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topPadding + 64.dp, bottom = bottomPadding, start = 16.dp, end = 16.dp),
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

                // iOS Style Search Bar
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isLightTheme) Color(0xFF767680).copy(0.12f) else Color(0xFF767680).copy(0.24f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.weight(1f),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = contentColor,
                                        fontSize = 15.sp
                                    ),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Search",
                                                    color = Color.Gray,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                                
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color.Gray,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { searchQuery = "" }
                                    )
                                }
                            }
                        }
                        if (searchQuery.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cancel",
                                color = if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF),
                                fontSize = 16.sp,
                                modifier = Modifier.clickable {
                                    searchQuery = ""
                                }
                            )
                        }
                    }
                }

                if (finalApps.isEmpty()) {
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
        }

        // Floating back button (header background removed, back button retains liquid glass style)
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
    }
}
