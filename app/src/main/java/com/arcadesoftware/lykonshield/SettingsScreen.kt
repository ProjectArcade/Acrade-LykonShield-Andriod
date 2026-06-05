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

            item { ProfileCard() }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "APPEARANCE",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                    ) {
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

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SHIELD SYSTEM",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                    ) {
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

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .clickable { onExcludeAppsClick() }
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                    ) {
                        SettingsRow(title = "App Version", value = BuildConfig.VERSION_NAME, showDivider = true)
                        SettingsRow(title = "Developer", value = "Arcade Software", showDivider = false)
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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

@Composable
fun IosThemeDialog(
    currentTheme: Int,
    onThemeSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White

    GlassCard(
        backdrop = backdrop,
        modifier = Modifier.width(270.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Appearance",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = textColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Choose how Lykon Shield looks on your device",
                fontSize = 13.sp,
                color = textColor.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            val options = listOf("Match System Theme", "Light Theme", "Dark Theme")

            options.forEachIndexed { index, title ->
                HorizontalDivider(
                    color = if (isLightTheme)
                        Color.Black.copy(alpha = 0.15f)
                    else
                        Color.White.copy(alpha = 0.15f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onThemeSelect(index)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, fontSize = 17.sp, color = textColor)
                    if (currentTheme == index) {
                        Text(
                            text = "✓",
                            color = Color(0xFF0A84FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            HorizontalDivider(
                color = if (isLightTheme)
                    Color.Black.copy(alpha = 0.15f)
                else
                    Color.White.copy(alpha = 0.15f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel",
                    color = Color.Red,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )
            }
        }
    }
}

@Composable
fun ProfileCard() {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White
    val cardBg = if (isLightTheme) Color.White else Color(0xFF1C1C1E)

    val iconResId = if (isLightTheme) R.drawable.ligh_icon else R.drawable.dark_icon

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