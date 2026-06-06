package com.arcadesoftware.lykonshield

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop

@Composable
fun UpdateBlockDialog(
    updateUrl: String,
    updateMessage: String,
    isHardUpdate: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val textColor = if (isLightTheme) Color.Black else Color.White
    val systemBlue = if (isLightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF)
    val context = LocalContext.current

    // Block all clicks and add a dimming backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(enabled = true) {}, 
        contentAlignment = Alignment.Center
    ) {
        IosBaseDialogCard(
            backdrop = backdrop
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row for Title and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.size(28.dp)) // Balance the close button
                    
                    Text(
                        text = if (isHardUpdate) "Update Required" else "Update Available",
                        color = textColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isLightTheme) Color.Black.copy(0.05f) else Color.White.copy(0.1f))
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

                Text(
                    text = updateMessage.ifEmpty { "A new update is available for Lykon Shield." },
                    color = if (isLightTheme) Color.DarkGray else Color.LightGray,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(if (isLightTheme) Color.Black.copy(0.08f) else Color.White.copy(0.1f))
                )

                if (isHardUpdate) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(systemBlue)
                            .clickable {
                                val finalUrl = if (updateUrl.isEmpty()) "https://github.com/ProjectArcade/Acrade-LykonShield/releases" else updateUrl
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                                context.startActivity(intent)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Update",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isLightTheme) Color(0xFFE5E5EA) else Color(0xFF2C2C2E))
                                .clickable { onDismiss() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Later",
                                color = systemBlue,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(systemBlue)
                                .clickable {
                                    val finalUrl = if (updateUrl.isEmpty()) "https://github.com/ProjectArcade/Acrade-LykonShield/releases" else updateUrl
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                                    context.startActivity(intent)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Update",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
