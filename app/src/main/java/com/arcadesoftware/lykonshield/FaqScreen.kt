package com.arcadesoftware.lykonshield

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
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

// Chevron Down Symbol for collapsible header indicator
private val ChevronDownIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ChevronDown",
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
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
    }.build()

data class FaqItem(val question: String, val answer: String)

@Composable
fun FaqScreen(
    navController: androidx.navigation.NavController,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    backdrop: Backdrop
) {
    val isLightTheme = LocalIsLightTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val screenContentBackdrop = rememberLayerBackdrop()

    val context = LocalContext.current
    val questions = remember { context.resources.getStringArray(R.array.faq_questions) }
    val answers = remember { context.resources.getStringArray(R.array.faq_answers) }

    val faqItems = remember(questions, answers) {
        questions.indices.map { idx ->
            FaqItem(questions[idx], answers.getOrElse(idx) { "" })
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val filteredFaq = remember(faqItems, searchQuery) {
        if (searchQuery.isEmpty()) {
            faqItems
        } else {
            faqItems.filter {
                it.question.contains(searchQuery, ignoreCase = true) ||
                it.answer.contains(searchQuery, ignoreCase = true)
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
                        text = "FAQ",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                if (filteredFaq.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No questions found",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                    }
                } else {
                    items(filteredFaq) { item ->
                        FaqCard(
                            item = item,
                            backdrop = backdrop,
                            contentColor = contentColor
                        )
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
                                        text = "Search FAQ...",
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

@Composable
fun FaqCard(
    item: FaqItem,
    backdrop: Backdrop,
    contentColor: Color
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "chevronRotation"
    )

    GlassCard(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.question,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = ChevronDownIcon,
                    contentDescription = "Toggle Expand",
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer(rotationZ = rotationAngle)
                        .padding(start = 4.dp)
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Text(
                    text = item.answer,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
