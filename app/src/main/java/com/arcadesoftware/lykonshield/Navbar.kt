package com.arcadesoftware.lykonshield

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.scale
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.*

// Lerp helper
private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}

// Gesture utilities
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        val drag = initialDown

        onDragStart(down)
        onDrag(drag, Offset.Zero)
        val upEvent = drag(
            pointerId = drag.id,
            onDrag = { onDrag(it, it.positionChange()) }
        )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val changes = currentEvent.changes
    var isPointerUp = true
    for (i in changes.indices) {
        if (changes[i].id == pointerId) {
            if (changes[i].pressed) {
                isPointerUp = false
            }
            break
        }
    }
    if (isPointerUp) return null

    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val changes = event.changes
        var dragEvent: PointerInputChange? = null
        for (i in changes.indices) {
            if (changes[i].id == pointer) {
                dragEvent = changes[i]
                break
            }
        }
        if (dragEvent == null) return null

        if (dragEvent.changedToUpIgnoreConsumed()) {
            var otherDown: PointerInputChange? = null
            for (i in changes.indices) {
                if (changes[i].pressed) {
                    otherDown = changes[i]
                    break
                }
            }
            if (otherDown == null) return dragEvent
            else pointer = otherDown.id
        } else {
            if (dragEvent.previousPosition != dragEvent.position) return dragEvent
        }
    }
}

// DampedDragAnimation
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { change, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            kotlinx.coroutines.android.awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            System.currentTimeMillis(),
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x /
                (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

// InteractiveHighlight
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShader(
            """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
        )
    } else {
        null
    }

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                drawRect(
                    Color.White.copy(0.08f * progress),
                    blendMode = BlendMode.Plus
                )
                shader.apply {
                    val pos = position(size, positionAnimation.value)
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(0.15f * progress).toArgb())
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        pos.x.coerceIn(0f, size.width),
                        pos.y.coerceIn(0f, size.height)
                    )
                }
                drawRect(
                    ShaderBrush(shader),
                    blendMode = BlendMode.Plus
                )
            } else {
                drawRect(
                    Color.White.copy(0.25f * progress),
                    blendMode = BlendMode.Plus
                )
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.snapTo(0f) }
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            }
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}

val LocalIsLightTheme = staticCompositionLocalOf { true }
val LocalIsLiquidGlassEnabled = staticCompositionLocalOf { true }

val ChevronLeftIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ChevronLeft",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
        strokeLineWidth = 2.5f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
    ) {
        moveTo(16f, 4f)
        lineTo(8f, 12f)
        lineTo(16f, 20f)
    }.build()

// LiquidBottomTab Scale CompositionLocal
internal val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }

@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scaleValue = scale()
                scaleX = scaleValue
                scaleY = scaleValue
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

// LiquidBottomTabs
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    height: Dp = 56.dp,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = LocalIsLightTheme.current
    val isLiquidGlass = LocalIsLiquidGlassEnabled.current
    val finalAccentColor = if (accentColor != Color.Unspecified) accentColor else (if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF))
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f) else Color(0xFF121212).copy(0.4f)
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val maxWidthPx = constraints.maxWidth.toFloat()
        val tabWidth = with(density) {
            (maxWidthPx - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).coerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                    android.util.Log.d("Navbar", "onDragStopped: targetValue=$targetValue, targetIndex=$targetIndex")
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        val currentOnTabSelected by rememberUpdatedState(onTabSelected)

        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    android.util.Log.d("Navbar", "LaunchedEffect: currentIndex changed to $index, navigating...")
                    dampedDragAnimation.animateToValue(index.toFloat())
                    currentOnTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Box(
            Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                Modifier
                    .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        if (isLiquidGlass) {
                            lens(24f.dp.toPx(), 24f.dp.toPx())
                        } else {
                            lens(12f.dp.toPx(), 12f.dp.toPx())
                        }
                    },
                    layerBlock = {
                        if (isLiquidGlass) {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        } else {
                            scaleX = 1f
                            scaleY = 1f
                        }
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(height + 8.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                if (isLiquidGlass) {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                } else {
                    1f
                }
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            if (isLiquidGlass) {
                                lens(
                                    24f.dp.toPx() * progress,
                                    24f.dp.toPx() * progress
                                )
                            } else {
                                lens(0f, 0f)
                            }
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(height)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(finalAccentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .offset {
                    val x = if (isLtr) selectedTabIndex() * tabWidth
                    else maxWidthPx - (selectedTabIndex() + 1f) * tabWidth
                    androidx.compose.ui.unit.IntOffset(x.roundToInt(), 0)
                }
                .graphicsLayer {
                    translationX = if (isLtr) {
                        (dampedDragAnimation.value - selectedTabIndex()) * tabWidth + panelOffset
                    } else {
                        -(dampedDragAnimation.value - selectedTabIndex()) * tabWidth + panelOffset
                    }
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        if (isLiquidGlass) {
                            lens(
                                10f.dp.toPx() * progress,
                                14f.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        } else {
                            lens(
                                5f.dp.toPx(),
                                7f.dp.toPx(),
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        if (isLiquidGlass) {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        } else {
                            scaleX = 1f
                            scaleY = 1f
                        }
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(height)
                .fillMaxWidth(1f / tabsCount)
        )
        }
    }
}

// CollapsibleTopBar
@Composable
fun CollapsibleTopBar(
    title: String,
    scrollOffsetProvider: () -> Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = LocalIsLightTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f) else Color(0xFF121212).copy(0.4f)

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = 56.dp + statusBarPadding

    val density = LocalDensity.current
    val maxScrollPx = with(density) { 40.dp.toPx() }

    val collapseFraction by remember(scrollOffsetProvider) {
        derivedStateOf {
            (scrollOffsetProvider() / maxScrollPx).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight)
            .graphicsLayer {
                alpha = collapseFraction
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    vibrancy()
                    blur(20f.dp.toPx())
                },
                onDrawSurface = { drawRect(containerColor) }
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

// LiquidButton — matches reference repo with generalized modifier & shape
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier.height(48.dp).padding(horizontal = 16.dp),
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    blurRadius: androidx.compose.ui.unit.Dp = 2.dp,
    lensRadius: androidx.compose.ui.unit.Dp = 12.dp,
    lensOffset: androidx.compose.ui.unit.Dp = 24.dp,
    chromaticAberration: Boolean = false,
    shape: () -> androidx.compose.ui.graphics.Shape = { Capsule() },
    content: @Composable RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    val isLiquidGlass = LocalIsLiquidGlassEnabled.current

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = {
                    vibrancy()
                    blur(blurRadius.toPx())
                    if (isLiquidGlass) {
                        lens(lensRadius.toPx(), lensOffset.toPx(), chromaticAberration = chromaticAberration)
                    } else {
                        lens(lensRadius.toPx() * 0.5f, lensOffset.toPx() * 0.5f, chromaticAberration = chromaticAberration)
                    }
                },
                layerBlock = if (isInteractive) {
                    {
                        if (isLiquidGlass) {
                            val width = size.width
                            val height = size.height

                            val progress = interactiveHighlight.pressProgress
                            val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                            val maxOffset = size.minDimension
                            val initialDerivative = 0.05f
                            val offset = interactiveHighlight.offset
                            translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                            translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                            val maxDragScale = 4f.dp.toPx() / size.height
                            val offsetAngle = atan2(offset.y, offset.x)
                            scaleX =
                                scale +
                                        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                        (width / height).coerceAtMost(1f)
                            scaleY =
                                scale +
                                        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                        (height / width).coerceAtMost(1f)
                        } else {
                            val progress = interactiveHighlight.pressProgress
                            val scale = lerp(1f, 0.96f, progress)
                            scaleX = scale
                            scaleY = scale
                        }
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                }
            )
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else androidx.compose.foundation.LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// LiquidToggle — matches reference repo exactly
@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = LocalIsLightTheme.current
    val isLiquidGlass = LocalIsLiquidGlassEnabled.current
    val accentColor =
        if (isLightTheme) Color(0xFF34C759)
        else Color(0xFF30D158)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { (if (isLiquidGlass) 20f.dp else 36f.dp).toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    onSelect(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (selected()) 0f else 1f
                    onSelect(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                fraction =
                    if (isLtr) (fraction + delta).coerceIn(0f, 1f)
                    else (fraction - delta).coerceIn(0f, 1f)
            }
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { fraction ->
                dampedDragAnimation.updateValue(fraction)
            }
    }
    LaunchedEffect(selected) {
        snapshotFlow { selected() }
            .collectLatest { isSelected ->
                val target = if (isSelected) 1f else 0f
                if (target != fraction) {
                    fraction = target
                    dampedDragAnimation.animateToValue(target)
                }
            }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    val fraction = dampedDragAnimation.value
                    drawRect(androidx.compose.ui.graphics.lerp(trackColor, accentColor, fraction))
                }
                .size(64.dp, 28.dp)
        )

        Box(
            Modifier
                .graphicsLayer {
                    val fraction = dampedDragAnimation.value
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, fraction)
                        else lerp(-padding, -(padding + dragWidth), fraction)
                }
                .semantics {
                    role = Role.Switch
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = if (isLiquidGlass) {
                        rememberCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                                val progress = dampedDragAnimation.pressProgress
                                val scaleX = lerp(2f / 3f, 0.75f, progress)
                                val scaleY = lerp(0f, 0.75f, progress)
                                scale(scaleX, scaleY) {
                                    drawBackdrop()
                                }
                            }
                        )
                    } else {
                        rememberCombinedBackdrop(backdrop, trackBackdrop)
                    },
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        if (isLiquidGlass) {
                            lens(
                                5f.dp.toPx() * progress,
                                10f.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        } else {
                            lens(
                                3f.dp.toPx(),
                                5f.dp.toPx(),
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        if (isLiquidGlass) {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 50f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        } else {
                            scaleX = 1f
                            scaleY = 1f
                        }
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(if (isLiquidGlass) 40.dp else 24.dp, 24.dp)
        )
    }
}

// LiquidSlider for 3 states (0, 1, 2)
@Composable
fun LiquidSlider(
    value: () -> Int, // 0, 1, 2
    onValueChange: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val isLightTheme = LocalIsLightTheme.current
        val isLiquidGlass = LocalIsLiquidGlassEnabled.current
        val trackColor = if (isLightTheme) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)
    
        val density = LocalDensity.current
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        
        val totalWidth = if (maxWidth != androidx.compose.ui.unit.Dp.Infinity) maxWidth else 120.dp
        val knobWidth = if (isLiquidGlass) 40.dp else 24.dp
        val dragWidthPx = with(density) { (totalWidth - 4.dp - knobWidth).toPx() }
        
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        var fraction by remember { mutableFloatStateOf(value().toFloat() / 2f) }
        
        val dampedDragAnimation = remember(animationScope, dragWidthPx) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = fraction,
                valueRange = 0f..1f,
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.2f,
                onDragStarted = {},
                onDragStopped = {
                    val targetValue = (this.targetValue * 2).roundToInt().coerceIn(0, 2)
                    fraction = targetValue / 2f
                    onValueChange(targetValue)
                    didDrag = false
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = dragAmount.x / dragWidthPx
                    fraction =
                        if (isLtr) (fraction + delta).coerceIn(0f, 1f)
                        else (fraction - delta).coerceIn(0f, 1f)
                }
            )
        }
        
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { fraction }
                .collectLatest { fraction ->
                    dampedDragAnimation.updateValue(fraction)
                }
        }
        LaunchedEffect(value) {
            snapshotFlow { value() }
                .collectLatest { v ->
                    val target = v.toFloat() / 2f
                    if (target != fraction) {
                        fraction = target
                        dampedDragAnimation.animateToValue(target)
                    }
                }
        }
    
        val trackBackdrop = rememberLayerBackdrop()
    
        Box(
            Modifier.width(totalWidth).height(24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .height(4.dp)
                    .layerBackdrop(trackBackdrop)
                    .clip(Capsule())
                    .drawBehind {
                        val w = size.width
                        val h = size.height
                        
                        val progress = dampedDragAnimation.value
                        
                        val dynamicColor = when {
                            progress < 0.5f -> androidx.compose.ui.graphics.lerp(Color(0xFF0A84FF), Color(0xFFFF9500), progress * 2f)
                            else -> androidx.compose.ui.graphics.lerp(Color(0xFFFF9500), Color(0xFF34C759), (progress - 0.5f) * 2f)
                        }
                        
                        drawRect(trackColor)
                        
                        val knobPx = knobWidth.toPx()
                        val startX = 2f.dp.toPx() + knobPx / 2f
                        val endX = w - (2f.dp.toPx() + knobPx / 2f)
                        val middleX = startX + (endX - startX) / 2f
                        
                        val knobCenter = startX + dragWidthPx * progress
                        
                        drawRect(
                            color = dynamicColor,
                            size = androidx.compose.ui.geometry.Size(knobCenter, h)
                        )
                        
                        // Partitions
                        val tickColor = Color.White.copy(alpha = 0.5f)
                        val tickWidth = 2f.dp.toPx()
                        drawRect(color = tickColor, topLeft = androidx.compose.ui.geometry.Offset(startX - tickWidth/2f, 0f), size = androidx.compose.ui.geometry.Size(tickWidth, h))
                        drawRect(color = tickColor, topLeft = androidx.compose.ui.geometry.Offset(middleX - tickWidth/2f, 0f), size = androidx.compose.ui.geometry.Size(tickWidth, h))
                        drawRect(color = tickColor, topLeft = androidx.compose.ui.geometry.Offset(endX - tickWidth/2f, 0f), size = androidx.compose.ui.geometry.Size(tickWidth, h))
                    }
                    .fillMaxWidth()
            )

        Box(
            Modifier
                .graphicsLayer {
                    val progress = dampedDragAnimation.value
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidthPx, progress)
                        else lerp(-padding, -(padding + dragWidthPx), progress)
                }
                .semantics {
                    // removed role = Role.Slider to fix Unresolved reference
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = if (isLiquidGlass) {
                        rememberCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                                val progress = dampedDragAnimation.pressProgress
                                val scaleX = lerp(2f / 3f, 0.75f, progress)
                                val scaleY = lerp(0f, 0.75f, progress)
                                scale(scaleX, scaleY) {
                                    drawBackdrop()
                                }
                            }
                        )
                    } else {
                        rememberCombinedBackdrop(backdrop, trackBackdrop)
                    },
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        if (isLiquidGlass) {
                            lens(
                                5f.dp.toPx() * progress,
                                10f.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        } else {
                            lens(
                                3f.dp.toPx(),
                                5f.dp.toPx(),
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        if (isLiquidGlass) {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 50f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        } else {
                            scaleX = 1f
                            scaleY = 1f
                        }
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .border(
                    width = 0.5.dp,
                    color = if (isLightTheme) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.3f),
                    shape = Capsule()
                )
                .size(knobWidth, 24.dp)
        )
    }
    }
    
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Light", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onValueChange(0) }, textAlign = androidx.compose.ui.text.style.TextAlign.Start)
            Text("Medium", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onValueChange(1) }, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Ultra", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onValueChange(2) }, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}
