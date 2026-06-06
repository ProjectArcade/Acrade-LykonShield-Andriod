package com.arcadesoftware.lykonshield

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop

@Composable
fun IosDeveloperDetailsDialog(
    onDismiss: () -> Unit,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White
    val systemBlue = if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(enabled = true) { onDismiss() }, 
        contentAlignment = Alignment.Center
    ) {
        IosBaseDialogCard(
            backdrop = backdrop
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = true) {}
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
                            text = "Developer Details",
                            color = textColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "About the developer",
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
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (isLightTheme) Color.Gray else Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.1f))
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "DEVELOPER", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Abhinav Thakur", color = textColor, fontSize = 15.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "ORGANIZATION", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = "ProjectArcade", color = textColor, fontSize = 15.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "DESCRIPTION", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Lykon Shield is open-source and dedicated to privacy-first community utility.",
                            color = textColor,
                            fontSize = 14.sp
                        )
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
                        text = "Close",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
