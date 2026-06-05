package com.arcadesoftware.lykonshield

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop

@Composable
fun SettingsScreen(
    state: LazyListState,
    themeMode: Int,
    onThemeClick: () -> Unit,
    onExcludeAppsClick: () -> Unit,
    onDeveloperClick: () -> Unit,
    isLiquidGlassEnabled: Boolean,
    onLiquidGlassToggle: (Boolean) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val cardBg = if (isLightTheme) Color.White else Color(0xFF1C1C1E)

    Box(modifier = Modifier.fillMaxSize()) {

        // List — no layerBackdrop wrapper, avoids layer conflict with dialog
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = bottomPadding + 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            item { ProfileCard(backdrop) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "APPEARANCE",
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
                            val currentThemeText = when (themeMode) {
                                1 -> "Light Theme"
                                2 -> "Dark Theme"
                                else -> "Match System Theme"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onThemeClick() }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Theme Preference", color = contentColor, fontSize = 16.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = currentThemeText, color = Color.Gray, fontSize = 16.sp)
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
                        text = "SHIELD SYSTEM",
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
                            SettingsRow(title = "Custom Shaders", value = "AGSL Active", showDivider = true)
                            SettingsRow(title = "Backdrop Blur Radius", value = "24dp", showDivider = true)
                            SettingsSwitchRow(
                                title = "Liquid Glass",
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
                GlassCard(
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onExcludeAppsClick() },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Bypass Apps Configuration",
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Exclude apps from shield blocking",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        Text(text = "〉", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "INFO",
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
                            SettingsRow(title = "App Version", value = BuildConfig.VERSION_NAME, showDivider = true)
                            SettingsRow(title = "Developer", value = "Arcade Software", showDivider = false)
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DEVELOPER",
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onDeveloperClick() }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Developer Options", color = contentColor, fontSize = 16.sp)
                                Text(text = "〉", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IosThemeDialog(
    currentTheme: Int,
    onThemeSelect: (Int) -> Unit,
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
                        text = "Appearance",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose theme preference",
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

            val options = listOf("Match System Theme", "Light Theme", "Dark Theme")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                options.forEachIndexed { index, title ->
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
                                onThemeSelect(index)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                        if (currentTheme == index) {
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
fun ProfileCard(backdrop: Backdrop) {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White

    val iconResId = if (isLightTheme) R.drawable.ligh_icon else R.drawable.dark_icon

    GlassCard(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = iconResId),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Column {
                Text(
                    text = "LykonShield",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = textColor
                )
                Text(
                    text = "Version 1.0.6 (Stable)",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    value: String,
    showDivider: Boolean
) {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White
    val valueColor = if (isLightTheme) Color.Gray else Color.LightGray

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = textColor, fontSize = 16.sp)
            Text(text = value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .padding(start = 16.dp)
                    .background(if (isLightTheme) Color(0xFFC7C7CC) else Color(0xFF38383A))
            )
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    showDivider: Boolean
) {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = textColor, fontSize = 16.sp)
            LiquidToggle(
                selected = { checked },
                onSelect = onCheckedChange,
                backdrop = backdrop
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .padding(start = 16.dp)
                    .background(if (isLightTheme) Color(0xFFC7C7CC) else Color(0xFF38383A))
            )
        }
    }
}