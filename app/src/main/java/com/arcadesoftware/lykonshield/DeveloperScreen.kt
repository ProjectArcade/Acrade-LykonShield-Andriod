package com.arcadesoftware.lykonshield

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
fun DeveloperScreen(
    navController: androidx.navigation.NavController,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    backdrop: Backdrop
) {
    val screenContentBackdrop = rememberLayerBackdrop()
    val isLightTheme = LocalIsLightTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val cardBg = if (isLightTheme) Color.White else Color(0xFF1C1C1E)

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
            contentPadding = PaddingValues(top = topPadding + 64.dp, bottom = bottomPadding, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = "Developer Options",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SHADER CONSOLE",
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
                        SettingsRow(title = "AGSL Compilation", value = "Success", showDivider = true)
                        SettingsRow(title = "Render Cache size", value = "512 MB", showDivider = false)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DEBUG TOOLS",
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
                        SettingsRow(title = "Highlight Captures", value = "Off", showDivider = true)
                        SettingsRow(title = "Force System Blur", value = "Enabled", showDivider = false)
                    }
                }
            }
        }
        }

        // Full width blurred header top bar matching ExcludeAppsScreen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp + topPadding)
                .drawBackdrop(
                    backdrop = screenContentBackdrop,
                    shape = { RectangleShape },
                    effects = {
                        vibrancy()
                        blur(20f.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f) else Color(0xFF121212).copy(0.4f))
                    }
                )
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
