package com.arcadesoftware.lykonshield

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

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
    val cardBg = if (isLightTheme) Color.White else Color(0xFF1C1C1E)
    val localBackdrop = rememberLayerBackdrop()

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding + 16.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Shield",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        item {
            ShieldStatusCard(enabled = isProtectionEnabled)
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PROTECTION STATISTICS",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatsRow(title = "Blocked Trackers", value = if (isProtectionEnabled) "1,248" else "0", color = Color(0xFFFF3B30))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(if (isLightTheme) Color(0xFFE5E5EA) else Color(0xFF38383A))
                        )
                        StatsRow(title = "Ads Blocked", value = if (isProtectionEnabled) "3,824" else "0", color = Color(0xFFFF9500))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(if (isLightTheme) Color(0xFFE5E5EA) else Color(0xFF38383A))
                        )
                        StatsRow(title = "Data Saved", value = if (isProtectionEnabled) "452 MB" else "0 MB", color = Color(0xFF34C759))
                    }
                }
            }
        }


    }
}

@Composable
fun ShieldStatusCard(enabled: Boolean) {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White
    val cardBg = if (isLightTheme) Color.White else Color(0xFF1C1C1E)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Color(0xFF34C759) else Color(0xFFFF3B30)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.6f))
                )
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
