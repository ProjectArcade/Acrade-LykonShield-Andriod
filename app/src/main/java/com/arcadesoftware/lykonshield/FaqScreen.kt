package com.arcadesoftware.lykonshield

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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

data class FaqData(val question: String, val answer: String)

val allFaqs = listOf(
    FaqData("What does the Blocker Filter Level do?", "The filter level determines how aggressive LykonShield is. Light blocks basic trackers, Medium includes annoyances, and Ultra provides maximum protection by blocking all known threats."),
    FaqData("Why do some apps stop working?", "Sometimes maximum protection (Ultra) blocks domains required for an app to function. You can bypass specific apps in the Bypass Apps Configuration in Settings."),
    FaqData("Does this app drain my battery?", "No, LykonShield uses an optimized local VPN service and highly efficient concurrent lists, meaning battery usage is extremely minimal."),
    FaqData("What is Liquid Glass?", "Liquid Glass is a premium aesthetic setting that adds real-time blurs, lighting, and chromatic aberration effects across the app's UI."),
    FaqData("How do I update filter lists?", "You can update the adblock engine filter lists by going to Settings > Update Shields Filter. The app also checks periodically in the background."),
    FaqData("Is my browsing data sent to any servers?", "Absolutely not. LykonShield operates entirely on your device using a local VPN profile. No traffic is routed to external proxy servers."),
    FaqData("Can I use another VPN at the same time?", "Android only allows one active VPN profile at a time. Activating LykonShield will disconnect any currently active VPN."),
    FaqData("How do I whitelist a specific website?", "Currently, LykonShield supports app-level bypasses. Website-level whitelisting will be introduced in an upcoming update.")
)

@Composable
fun FaqScreen(
    onBackClick: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val cardBg = if (isLightTheme) Color.White else Color(0xFF1C1C1E)
    val bgColor = if (isLightTheme) Color(0xFFF2F2F7) else Color.Black
    val screenContentBackdrop = rememberLayerBackdrop()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val filteredFaqs = remember(searchQuery) {
        if (searchQuery.isBlank()) allFaqs
        else allFaqs.filter { it.question.contains(searchQuery, ignoreCase = true) || it.answer.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(screenContentBackdrop)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, 
                    end = 16.dp, 
                    top = topPadding + 64.dp, 
                    bottom = bottomPadding + 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                Text(
                    text = "FAQ & Help",
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = contentColor,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
            if (filteredFaqs.isEmpty()) {
                item {
                    Text(
                        text = "No results found for \"$searchQuery\"",
                        color = Color.Gray,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(filteredFaqs) { faq ->
                    FaqCollapsibleItem(
                        faq = faq,
                        cardBg = cardBg,
                        contentColor = contentColor
                    )
                }
            }
        }
        } // close layerBackdrop box

        // Floating back button (top-left)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp + topPadding)
                .padding(top = topPadding, start = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            LiquidButton(
                onClick = onBackClick,
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

        // Expanded search bar (bottom)
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
                                        text = "Search FAQs...",
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

        // FAB search button
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

@Composable
fun FaqCollapsibleItem(
    faq: FaqData,
    cardBg: Color,
    contentColor: Color
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .clickable { isExpanded = !isExpanded }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = faq.question,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isExpanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp 
                              else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
        ) {
            Text(
                text = faq.answer,
                fontSize = 14.sp,
                color = Color.Gray,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
