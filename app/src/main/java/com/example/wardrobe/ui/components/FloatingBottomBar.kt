package com.example.wardrobe.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppleTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class ScreenTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "tab_home"),
    WARDROBE("Wardrobe", Icons.Filled.Checkroom, Icons.Outlined.Checkroom, "tab_wardrobe"),
    OUTFITS("Outfits", Icons.Filled.Style, Icons.Outlined.Style, "tab_outfits"),
    AI("AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "tab_ai"),
    PLANNER("Planner", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "tab_planner"),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person, "tab_profile")
}

/**
 * Custom CubicBezier easing curves tuned for high-end mobile navigation bars:
 * - [PillStrokeAccelerationEasing]: rapid non-linear initial acceleration with a long, fluid deceleration tail.
 * - [PillStrokeSettleEasing]: smooth S-curve inflection for settling stroke weight back to rest.
 */
private val PillStrokeAccelerationEasing = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)
private val PillStrokeSettleEasing = CubicBezierEasing(0.33f, 0.0f, 0.14f, 1.0f)

/**
 * Pill-Shaped Bottom Navigation Bar with Single Spring-Traveling Active Pill Outline & Haptic Feedback:
 * 1. Outer navbar container is full pill-shaped (`CircleShape`) with a crisp pill border.
 * 2. ONLY the currently active/present icon has the inner pill outline (`CircleShape`); unselected icons have NO pill outline.
 * 3. When another icon is clicked, that single active pill outline stretches and travels to the newly selected icon
 *    using differential leading/trailing spring physics (`dampingRatio = 0.50f..0.68f`) and bouncy overshoot,
 *    while its stroke-width transitions via a custom [CubicBezierEasing] curve.
 * 4. Triggers `performHapticFeedback` when the pill-shaped icon state changes (on press, tab switch, and spring settle).
 */
@Composable
fun FloatingBottomBar(
    currentTab: ScreenTab,
    onTabSelected: (ScreenTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppleTheme.colors
    val isDark = colors.isDark
    val tabs = ScreenTab.values()
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    // Non-linear CubicBezier stroke-width animatable for the traveling navigation pill outline
    val pillStrokePulse = remember { Animatable(0f) }

    // Track travel direction (left vs right) and fire haptic feedback + CubicBezier stroke transition
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    var isFirstComposition by remember { mutableStateOf(true) }
    val movingRight = selectedIndex >= previousIndex

    LaunchedEffect(selectedIndex) {
        if (isFirstComposition) {
            isFirstComposition = false
        } else if (selectedIndex != previousIndex) {
            // Trigger haptic feedback when the pill-shaped icon state changes to a new destination
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            // Animate navigation pill stroke-width expansion & settle using custom CubicBezier easing
            pillStrokePulse.snapTo(0f)
            pillStrokePulse.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 190,
                    easing = PillStrokeAccelerationEasing
                )
            )
            pillStrokePulse.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 260,
                    easing = PillStrokeSettleEasing
                )
            )
        }
        previousIndex = selectedIndex
    }

    // Reference colors with crisp active pill outline
    val barBackground = if (isDark) Color(0xFF141820) else Color(0xFFEEF3F9)
    val barBorderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color(0xFFCFD9E6)
    val activeCardBg = if (isDark) Color(0xFF222834) else Color(0xFFFFFFFF)
    val activeCardBorder = if (isDark) Color(0xFF2997FF).copy(alpha = 0.62f) else Color(0xFF9AC5F4)
    val activeBlue = if (isDark) Color(0xFF2997FF) else Color(0xFF0067BA)
    val waveCrestBlue = if (isDark) Color(0xFF70BFFF) else Color(0xFF47B0FF)
    val inactiveIconColor = if (isDark) Color(0xFF9CA5B4) else Color(0xFF636C76)
    val inactiveLabelColor = if (isDark) Color(0xFF9CA5B4) else Color(0xFF4E5660)

    // Both outer navbar and the single traveling active icon outline use CircleShape (full pill shape)
    val outerPillShape = CircleShape
    val innerPillShape = CircleShape
    val itemHeight = 56.dp
    val itemSpacing = 4.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 468.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDark) 16.dp else 12.dp,
                    shape = outerPillShape,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.48f else 0.14f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.58f else 0.18f)
                )
                .clip(outerPillShape)
                .background(barBackground)
                .border(width = 1.3.dp, color = barBorderColor, shape = outerPillShape)
                .padding(horizontal = 6.dp, vertical = 7.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
            ) {
                val totalSpacing = itemSpacing * (tabs.size - 1)
                val itemWidth = ((maxWidth - totalSpacing) / tabs.size).coerceAtLeast(42.dp)
                val stride = itemWidth + itemSpacing

                // 1. Single Traveling Active Pill Outline with Differential Spring Physics:
                // Only the present icon has a pill outline, and when another icon is clicked,
                // its leading edge leaps forward while its trailing edge follows with bouncy spring physics!
                val targetCardLeft = stride * selectedIndex
                val targetCardRight = targetCardLeft + itemWidth

                val leadingOutlineSpring = spring<Dp>(
                    dampingRatio = 0.66f,
                    stiffness = 440f
                )
                val trailingOutlineSpring = spring<Dp>(
                    dampingRatio = 0.50f,
                    stiffness = 200f
                )

                val activeOutlineLeft by animateDpAsState(
                    targetValue = targetCardLeft,
                    animationSpec = if (movingRight) trailingOutlineSpring else leadingOutlineSpring,
                    label = "active_outline_left"
                )
                val activeOutlineRight by animateDpAsState(
                    targetValue = targetCardRight,
                    animationSpec = if (movingRight) leadingOutlineSpring else trailingOutlineSpring,
                    label = "active_outline_right"
                )

                val currentOutlineStart = minOf(activeOutlineLeft, activeOutlineRight)
                val currentOutlineWidth = abs((activeOutlineRight - activeOutlineLeft).value)
                    .coerceAtLeast(itemWidth.value * 0.82f).dp

                // Vertical squash-and-bounce as the pill outline stretches across tabs
                val stretchRatio = ((currentOutlineWidth.value - itemWidth.value) / stride.value.coerceAtLeast(1f))
                    .coerceIn(0f, 1.2f)
                val activeOutlineScaleY by animateFloatAsState(
                    targetValue = 1f - (0.09f * stretchRatio),
                    animationSpec = spring(
                        dampingRatio = 0.48f,
                        stiffness = 320f
                    ),
                    label = "active_outline_scale_y"
                )

                // Navigation pill stroke-width driven by custom CubicBezier easing + in-flight stretch
                val easedStretchFactor = PillStrokeAccelerationEasing.transform(
                    (stretchRatio / 1.2f).coerceIn(0f, 1f)
                )
                val targetPillStrokeWidth = 1.35.dp +
                    (0.85.dp * maxOf(pillStrokePulse.value, easedStretchFactor))
                val animatedPillStrokeWidth by animateDpAsState(
                    targetValue = targetPillStrokeWidth,
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = PillStrokeAccelerationEasing
                    ),
                    label = "active_pill_stroke_width"
                )

                // 2. Differential-Speed Stretching Bottom Accent Pill Indicator:
                val restPillWidth = 22.dp
                val restPillStartInCard = (itemWidth - restPillWidth) / 2
                val targetPillLeft = stride * selectedIndex + restPillStartInCard
                val targetPillRight = targetPillLeft + restPillWidth

                val fastLeadingSpring = spring<Dp>(
                    dampingRatio = 0.74f,
                    stiffness = 600f
                )
                val bouncyTrailingSpring = spring<Dp>(
                    dampingRatio = 0.50f,
                    stiffness = 210f
                )

                val pillLeft by animateDpAsState(
                    targetValue = targetPillLeft,
                    animationSpec = if (movingRight) bouncyTrailingSpring else fastLeadingSpring,
                    label = "pill_left_edge"
                )
                val pillRight by animateDpAsState(
                    targetValue = targetPillRight,
                    animationSpec = if (movingRight) fastLeadingSpring else bouncyTrailingSpring,
                    label = "pill_right_edge"
                )

                val currentPillStart = minOf(pillLeft, pillRight)
                val currentPillWidth = abs((pillRight - pillLeft).value)
                    .coerceAtLeast(restPillWidth.value * 0.75f).dp

                // Single Active Pill Outline & Card (moves from current icon to clicked icon with spring physics)
                Box(
                    modifier = Modifier
                        .offset(x = currentOutlineStart)
                        .size(width = currentOutlineWidth, height = itemHeight)
                        .graphicsLayer {
                            scaleY = activeOutlineScaleY
                        }
                        .shadow(
                            elevation = if (isDark) 6.dp else 4.dp,
                            shape = innerPillShape,
                            ambientColor = activeBlue.copy(alpha = 0.16f),
                            spotColor = activeBlue.copy(alpha = 0.24f)
                        )
                        .clip(innerPillShape)
                        .background(activeCardBg)
                        .border(
                            width = animatedPillStrokeWidth,
                            color = activeCardBorder.copy(
                                alpha = (activeCardBorder.alpha + 0.22f * pillStrokePulse.value).coerceIn(0f, 1f)
                            ),
                            shape = innerPillShape
                        )
                )

                // Stretching Blue Pill Indicator along bottom of active pill
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = currentPillStart, y = (-4).dp)
                        .width(currentPillWidth)
                        .height(3.5.dp)
                        .clip(CircleShape)
                        .background(activeBlue)
                )

                // 3. Horizontal Row of Navigation Items (unselected items have NO pill outline)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val isSelected = tab == currentTab
                        ReferenceBottomNavItem(
                            tab = tab,
                            isSelected = isSelected,
                            itemWidth = itemWidth,
                            itemHeight = itemHeight,
                            activeBlue = activeBlue,
                            waveCrestBlue = waveCrestBlue,
                            inactiveIconColor = inactiveIconColor,
                            inactiveLabelColor = inactiveLabelColor,
                            onClick = {
                                if (tab != currentTab) {
                                    onTabSelected(tab)
                                } else {
                                    // Subtle haptic pulse even when re-tapping the active pill icon
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceBottomNavItem(
    tab: ScreenTab,
    isSelected: Boolean,
    itemWidth: Dp,
    itemHeight: Dp,
    activeBlue: Color,
    waveCrestBlue: Color,
    inactiveIconColor: Color,
    inactiveLabelColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    // Trigger subtle tactile haptic feedback when user presses a navigation item
    LaunchedEffect(isPressed) {
        if (isPressed) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Combined stroke-draw + liquid-wave fill + halo/sparkle progress: 0f -> 1f
    val animProgress = remember { Animatable(if (isSelected) 1f else 0f) }
    var wasSelected by remember { mutableStateOf(isSelected) }

    LaunchedEffect(isSelected) {
        val stateChanged = wasSelected != isSelected
        wasSelected = isSelected
        if (isSelected) {
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = PillStrokeAccelerationEasing
                )
            )
            // Haptic confirmation pulse when the pill-shaped icon finishes its liquid-fill + spring settle
            if (stateChanged) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } else {
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 240,
                    easing = PillStrokeSettleEasing
                )
            )
        }
    }

    // Expressive, clearly visible spring bounce on the icon when pressed/selected
    val iconSpringScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.76f
            isSelected && animProgress.value in 0.01f..0.22f -> 0.80f
            isSelected && animProgress.value in 0.45f..0.88f -> 1.22f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = 0.42f, // Visible bouncy overshoot
            stiffness = 340f
        ),
        label = "icon_spring_scale"
    )

    // Unselected icon sits above the label (-6.5.dp); selected icon springs to the center (-1.dp)
    val iconOffsetY by animateDpAsState(
        targetValue = if (isSelected) (-1).dp else (-6.5).dp,
        animationSpec = spring(
            dampingRatio = 0.50f,
            stiffness = 300f
        ),
        label = "icon_offset_y"
    )

    // Label fades & springs down out of sight when selected
    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (isSelected) 150 else 240,
            easing = FastOutSlowInEasing
        ),
        label = "label_alpha"
    )

    val labelTranslationY by animateFloatAsState(
        targetValue = if (isSelected) 10f else 0f,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = 320f
        ),
        label = "label_translation_y"
    )

    Box(
        modifier = Modifier
            .size(width = itemWidth, height = itemHeight)
            .clip(CircleShape)
            .testTag(tab.testTag)
            .semantics {
                role = Role.Tab
                selected = isSelected
                contentDescription = tab.title
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .offset(y = iconOffsetY)
                .size(26.dp)
                .graphicsLayer {
                    scaleX = iconSpringScale
                    scaleY = iconSpringScale
                }
        ) {
            val p = animProgress.value
            val isTransitioning = isSelected && p in 0.01f..0.98f
            val strokeColor = if (isSelected || isPressed || p > 0.03f) activeBlue else inactiveIconColor

            // 1. Expanding circular halo arc & 3 four-point star sparkles during transition
            if (isSelected && p in 0.16f..0.96f) {
                drawTransitionSparklesAndRing(
                    progress = p,
                    primaryColor = activeBlue,
                    secondaryColor = waveCrestBlue
                )
            }

            // 2. Draw icon with progressive stroke-drawing + rising two-tone liquid wave fill
            when (tab) {
                ScreenTab.HOME -> drawHomeAnimatedIcon(
                    progress = p,
                    isTransitioning = isTransitioning,
                    strokeColor = strokeColor,
                    fillColor = activeBlue,
                    waveColor = waveCrestBlue
                )
                ScreenTab.WARDROBE -> drawWardrobeAnimatedIcon(
                    progress = p,
                    isTransitioning = isTransitioning,
                    strokeColor = strokeColor,
                    fillColor = activeBlue,
                    waveColor = waveCrestBlue
                )
                ScreenTab.OUTFITS -> drawOutfitsAnimatedIcon(
                    progress = p,
                    isTransitioning = isTransitioning,
                    strokeColor = strokeColor,
                    fillColor = activeBlue,
                    waveColor = waveCrestBlue
                )
                ScreenTab.AI -> drawAiAnimatedIcon(
                    progress = p,
                    isTransitioning = isTransitioning,
                    strokeColor = strokeColor,
                    fillColor = activeBlue,
                    waveColor = waveCrestBlue
                )
                ScreenTab.PLANNER -> drawPlannerAnimatedIcon(
                    progress = p,
                    isTransitioning = isTransitioning,
                    strokeColor = strokeColor,
                    fillColor = activeBlue,
                    waveColor = waveCrestBlue
                )
                ScreenTab.PROFILE -> drawProfileAnimatedIcon(
                    progress = p,
                    isTransitioning = isTransitioning,
                    strokeColor = strokeColor,
                    fillColor = activeBlue,
                    waveColor = waveCrestBlue
                )
            }
        }

        // Bottom Label inside the pill slot (visible when unselected, hides when selected)
        Text(
            text = tab.title,
            fontSize = 10.sp,
            fontWeight = if (isPressed) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isPressed) activeBlue else inactiveLabelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .graphicsLayer {
                    alpha = labelAlpha
                    translationY = labelTranslationY
                }
        )
    }
}

/**
 * Progressive stroke-drawing helper with custom CubicBezier stroke-width modulation:
 * Animates both the stroke path length and the dynamic stroke width using [PillStrokeAccelerationEasing]
 * and [PillStrokeSettleEasing] for fluid, non-linear acceleration before blending into the liquid fill.
 */
private fun DrawScope.drawProgressiveStrokePath(
    path: Path,
    progress: Float,
    isTransitioning: Boolean,
    strokeColor: Color,
    accentGlowColor: Color,
    strokeWidth: Float
) {
    if (progress >= 0.90f) return

    val fadeOutAlpha = (1f - (progress - 0.68f).coerceAtLeast(0f) / 0.22f).coerceIn(0f, 1f)

    if (!isTransitioning || progress <= 0.01f) {
        // Slightly crisper, thinner resting stroke when unselected
        drawPath(
            path = path,
            color = strokeColor.copy(alpha = fadeOutAlpha),
            style = Stroke(width = strokeWidth * 0.92f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        return
    }

    val rawStrokePhase = (progress / 0.55f).coerceIn(0f, 1f)
    val strokePhase = PillStrokeAccelerationEasing.transform(rawStrokePhase)

    // Non-linear stroke-width swell during drawing phase, then settling smoothly via CubicBezier
    val widthModulation = if (rawStrokePhase < 0.5f) {
        val up = PillStrokeAccelerationEasing.transform((rawStrokePhase * 2f).coerceIn(0f, 1f))
        0.92f + (0.30f * up) // swells from 0.92x -> 1.22x
    } else {
        val down = PillStrokeSettleEasing.transform(((rawStrokePhase - 0.5f) * 2f).coerceIn(0f, 1f))
        1.22f - (0.22f * down) // settles from 1.22x -> 1.00x
    }
    val dynamicStrokeWidth = strokeWidth * widthModulation

    drawPath(
        path = path,
        color = strokeColor.copy(alpha = 0.25f * fadeOutAlpha),
        style = Stroke(width = strokeWidth * 0.88f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    val androidPath = path.asAndroidPath()
    val pm = android.graphics.PathMeasure(androidPath, false)
    val segmentAndroidPath = android.graphics.Path()
    val tipAndroidPath = android.graphics.Path()

    do {
        val len = pm.length
        if (len > 0f) {
            val stop = len * strokePhase
            pm.getSegment(0f, stop, segmentAndroidPath, true)
            if (strokePhase in 0.05f..0.95f) {
                val tipStart = (stop - len * 0.18f).coerceAtLeast(0f)
                pm.getSegment(tipStart, stop, tipAndroidPath, true)
            }
        }
    } while (pm.nextContour())

    drawPath(
        path = segmentAndroidPath.asComposePath(),
        color = strokeColor.copy(alpha = fadeOutAlpha),
        style = Stroke(width = dynamicStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    if (strokePhase in 0.05f..0.95f) {
        drawPath(
            path = tipAndroidPath.asComposePath(),
            color = accentGlowColor.copy(alpha = fadeOutAlpha),
            style = Stroke(width = dynamicStrokeWidth * 1.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Draws the expanding circular halo arc and 3 four-point micro-sparkles around the active icon.
 */
private fun DrawScope.drawTransitionSparklesAndRing(
    progress: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    val norm = ((progress - 0.16f) / 0.76f).coerceIn(0f, 1f)
    val center = Offset(size.width / 2f, size.height / 2f)

    val ringAlpha = (1f - norm) * 0.52f
    val ringRadius = (size.minDimension * 0.34f) + (size.minDimension * 0.22f * norm)
    drawArc(
        color = secondaryColor.copy(alpha = ringAlpha),
        startAngle = -140f + norm * 65f,
        sweepAngle = 225f * (1f - norm * 0.3f),
        useCenter = false,
        topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
        size = Size(ringRadius * 2f, ringRadius * 2f),
        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
    )

    val sparkleScale = sin(norm * PI).toFloat().coerceIn(0f, 1f)
    if (sparkleScale > 0.05f) {
        val dist = size.minDimension * (0.36f + 0.15f * norm)
        val angles = listOf(-135f, -40f, 35f)
        angles.forEachIndexed { idx, angleDeg ->
            val rad = angleDeg * (PI / 180.0)
            val sx = center.x + (cos(rad) * dist).toFloat()
            val sy = center.y + (sin(rad) * dist).toFloat()
            val arm = (if (idx == 0) 2.8.dp else 2.2.dp).toPx() * sparkleScale
            val color = if (idx % 2 == 0) primaryColor else secondaryColor

            drawLine(
                color = color,
                start = Offset(sx - arm, sy),
                end = Offset(sx + arm, sy),
                strokeWidth = 1.3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(sx, sy - arm),
                end = Offset(sx, sy + arm),
                strokeWidth = 1.3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Rising two-tone liquid wave fill inside any closed [shapePath].
 */
private fun DrawScope.drawLiquidWaveFill(
    shapePath: Path,
    progress: Float,
    fillColor: Color,
    waveColor: Color,
    boundsTop: Float,
    boundsBottom: Float
) {
    if (progress <= 0.14f) return

    val fillPhase = ((progress - 0.14f) / 0.76f).coerceIn(0f, 1f)
    if (fillPhase >= 0.99f) {
        drawPath(path = shapePath, color = fillColor, style = Fill)
        return
    }

    val totalH = boundsBottom - boundsTop
    val currentY = boundsBottom - totalH * fillPhase
    val waveAmp = (2.6.dp.toPx()) * sin(fillPhase * PI).toFloat()
    val w = size.width

    val crestPath = Path().apply {
        val crestY = (currentY - 2.0.dp.toPx()).coerceAtLeast(boundsTop - 4f)
        moveTo(0f, boundsBottom + 4f)
        lineTo(0f, crestY)
        cubicTo(
            w * 0.30f, crestY - waveAmp,
            w * 0.70f, crestY + waveAmp,
            w, crestY
        )
        lineTo(w, boundsBottom + 4f)
        close()
    }

    val bodyPath = Path().apply {
        moveTo(0f, boundsBottom + 4f)
        lineTo(0f, currentY)
        cubicTo(
            w * 0.35f, currentY + waveAmp,
            w * 0.65f, currentY - waveAmp,
            w, currentY
        )
        lineTo(w, boundsBottom + 4f)
        close()
    }

    clipPath(shapePath) {
        drawPath(path = crestPath, color = waveColor, style = Fill)
        drawPath(path = bodyPath, color = fillColor, style = Fill)
    }
}

/**
 * 1. HOME ICON (Pill-rounded house & pill-arch door)
 */
private fun DrawScope.drawHomeAnimatedIcon(
    progress: Float,
    isTransitioning: Boolean,
    strokeColor: Color,
    fillColor: Color,
    waveColor: Color
) {
    val w = size.width
    val h = size.height
    val strokeW = 1.8.dp.toPx()

    val left = w * 0.19f
    val right = w * 0.81f
    val top = h * 0.17f
    val roofBaseY = h * 0.46f
    val bottom = h * 0.83f
    val centerX = w * 0.50f

    val houseOuter = Path().apply {
        moveTo(centerX, top)
        lineTo(right, roofBaseY)
        lineTo(right, bottom - 5.dp.toPx())
        quadraticTo(right, bottom, right - 5.dp.toPx(), bottom)
        lineTo(left + 5.dp.toPx(), bottom)
        quadraticTo(left, bottom, left, bottom - 5.dp.toPx())
        lineTo(left, roofBaseY)
        close()
    }

    // Pill-shaped inner door outline
    val doorWidth = w * 0.18f
    val doorTop = h * 0.53f
    val doorBottom = bottom - 2.5.dp.toPx()
    val doorCorner = CornerRadius(doorWidth / 2f, doorWidth / 2f)
    val doorPath = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(centerX - doorWidth / 2f, doorTop, centerX + doorWidth / 2f, doorBottom),
                cornerRadius = doorCorner
            )
        )
    }

    val houseWithDoorCutout = Path().apply {
        op(houseOuter, doorPath, PathOperation.Difference)
    }

    drawLiquidWaveFill(
        shapePath = houseWithDoorCutout,
        progress = progress,
        fillColor = fillColor,
        waveColor = waveColor,
        boundsTop = top,
        boundsBottom = bottom
    )

    val combinedStrokePath = Path().apply {
        addPath(houseOuter)
        addPath(doorPath)
    }
    drawProgressiveStrokePath(
        path = combinedStrokePath,
        progress = progress,
        isTransitioning = isTransitioning,
        strokeColor = strokeColor,
        accentGlowColor = waveColor,
        strokeWidth = strokeW
    )
}

/**
 * 2. WARDROBE ICON (Hanger with pill-shaped inner bar outline)
 */
private fun DrawScope.drawWardrobeAnimatedIcon(
    progress: Float,
    isTransitioning: Boolean,
    strokeColor: Color,
    fillColor: Color,
    waveColor: Color
) {
    val w = size.width
    val h = size.height
    val strokeW = 1.8.dp.toPx()

    val tiltDeg = if (progress in 0.04f..0.78f) {
        sin((progress - 0.04f) / 0.74f * PI * 2.0).toFloat() * 8f
    } else 0f

    withTransform({
        rotate(degrees = tiltDeg, pivot = Offset(w * 0.5f, h * 0.30f))
    }) {
        val centerX = w * 0.50f
        val left = w * 0.14f
        val right = w * 0.86f
        val shoulderY = h * 0.44f
        val bottomY = h * 0.78f

        val hangerBody = Path().apply {
            moveTo(centerX, shoulderY)
            lineTo(right - 2.dp.toPx(), bottomY - 3.5.dp.toPx())
            quadraticTo(right, bottomY, right - 4.5.dp.toPx(), bottomY)
            lineTo(left + 4.5.dp.toPx(), bottomY)
            quadraticTo(left, bottomY, left + 2.dp.toPx(), bottomY - 3.5.dp.toPx())
            close()
        }

        // Pill-shaped inner slot inside hanger
        val pillSlotRect = Rect(
            left = w * 0.32f,
            top = h * 0.63f,
            right = w * 0.68f,
            bottom = h * 0.71f
        )
        val innerPillCutout = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = pillSlotRect,
                    cornerRadius = CornerRadius(pillSlotRect.height / 2f, pillSlotRect.height / 2f)
                )
            )
        }

        val hangerWithPillCutout = Path().apply {
            op(hangerBody, innerPillCutout, PathOperation.Difference)
        }

        val hookPath = Path().apply {
            moveTo(centerX - 2.5.dp.toPx(), h * 0.25f)
            cubicTo(
                centerX - 2.5.dp.toPx(), h * 0.14f,
                centerX + 4.dp.toPx(), h * 0.14f,
                centerX + 3.5.dp.toPx(), h * 0.26f
            )
            cubicTo(
                centerX + 3.5.dp.toPx(), h * 0.33f,
                centerX, h * 0.36f,
                centerX, shoulderY
            )
        }

        drawLiquidWaveFill(
            shapePath = hangerWithPillCutout,
            progress = progress,
            fillColor = fillColor,
            waveColor = waveColor,
            boundsTop = h * 0.16f,
            boundsBottom = bottomY
        )

        if (progress >= 0.90f) {
            drawPath(
                path = hookPath,
                color = fillColor,
                style = Stroke(
                    width = strokeW * 1.15f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        } else {
            val fullHangerStroke = Path().apply {
                addPath(hookPath)
                addPath(hangerBody)
                addPath(innerPillCutout)
            }
            drawProgressiveStrokePath(
                path = fullHangerStroke,
                progress = progress,
                isTransitioning = isTransitioning,
                strokeColor = strokeColor,
                accentGlowColor = waveColor,
                strokeWidth = strokeW
            )
        }
    }
}

/**
 * 3. OUTFITS ICON (4-tile grid with pill-rounded inner outlines & rotating top-right tile)
 */
private fun DrawScope.drawOutfitsAnimatedIcon(
    progress: Float,
    isTransitioning: Boolean,
    strokeColor: Color,
    fillColor: Color,
    waveColor: Color
) {
    val w = size.width
    val h = size.height
    val strokeW = 1.7.dp.toPx()

    val scatter = if (progress in 0.04f..0.78f) {
        sin((progress - 0.04f) / 0.74f * PI).toFloat() * 1.8.dp.toPx()
    } else 0f

    val boxSize = w * 0.27f
    val gap = w * 0.05f
    val centerX = w * 0.48f
    val centerY = h * 0.52f
    val pillCorner = CornerRadius(boxSize * 0.42f, boxSize * 0.42f)

    val tlRect = Rect(
        left = centerX - gap / 2f - boxSize - scatter,
        top = centerY - gap / 2f - boxSize - scatter,
        right = centerX - gap / 2f - scatter,
        bottom = centerY - gap / 2f - scatter
    )
    val blRect = Rect(
        left = centerX - gap / 2f - boxSize - scatter,
        top = centerY + gap / 2f + scatter,
        right = centerX - gap / 2f - scatter,
        bottom = centerY + gap / 2f + boxSize + scatter
    )
    val brRect = Rect(
        left = centerX + gap / 2f + scatter,
        top = centerY + gap / 2f + scatter,
        right = centerX + gap / 2f + boxSize + scatter,
        bottom = centerY + gap / 2f + boxSize + scatter
    )

    val gridPath = Path().apply {
        addRoundRect(RoundRect(tlRect, pillCorner))
        addRoundRect(RoundRect(blRect, pillCorner))
        addRoundRect(RoundRect(brRect, pillCorner))
    }

    val trCenter = Offset(
        x = centerX + gap / 2f + boxSize * 0.55f + scatter,
        y = centerY - gap / 2f - boxSize * 0.55f - scatter
    )
    val extraSpin = if (progress in 0.02f..0.85f) {
        ((progress - 0.02f) / 0.83f).coerceIn(0f, 1f) * 90f
    } else 0f

    drawLiquidWaveFill(
        shapePath = gridPath,
        progress = progress,
        fillColor = fillColor,
        waveColor = waveColor,
        boundsTop = tlRect.top - 4f,
        boundsBottom = brRect.bottom + 2f
    )

    drawProgressiveStrokePath(
        path = gridPath,
        progress = progress,
        isTransitioning = isTransitioning,
        strokeColor = strokeColor,
        accentGlowColor = waveColor,
        strokeWidth = strokeW
    )

    withTransform({
        rotate(degrees = 45f + extraSpin, pivot = trCenter)
    }) {
        val half = boxSize * 0.46f
        val diamondPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(trCenter.x - half, trCenter.y - half, trCenter.x + half, trCenter.y + half),
                    cornerRadius = pillCorner
                )
            )
        }
        if (progress > 0.22f) {
            drawPath(
                path = diamondPath,
                color = if (progress < 0.52f) waveColor else fillColor,
                style = Fill
            )
        }
        drawProgressiveStrokePath(
            path = diamondPath,
            progress = progress,
            isTransitioning = isTransitioning,
            strokeColor = strokeColor,
            accentGlowColor = waveColor,
            strokeWidth = strokeW
        )
    }
}

/**
 * 4. PLANNER ICON (Calendar with pill-shaped inner date capsules)
 */
private fun DrawScope.drawPlannerAnimatedIcon(
    progress: Float,
    isTransitioning: Boolean,
    strokeColor: Color,
    fillColor: Color,
    waveColor: Color
) {
    val w = size.width
    val h = size.height
    val strokeW = 1.8.dp.toPx()

    val left = w * 0.18f
    val right = w * 0.82f
    val top = h * 0.23f
    val bottom = h * 0.82f
    val corner = CornerRadius(5.dp.toPx(), 5.dp.toPx())

    val calBody = Path().apply {
        addRoundRect(RoundRect(Rect(left, top, right, bottom), corner))
    }

    // Pill-shaped inner schedule bars inside the calendar
    val pillCutouts = Path().apply {
        val pillH = 2.6.dp.toPx()
        val pillR = CornerRadius(pillH / 2f, pillH / 2f)
        addRoundRect(
            RoundRect(
                rect = Rect(w * 0.30f, h * 0.50f - pillH / 2f, w * 0.70f, h * 0.50f + pillH / 2f),
                cornerRadius = pillR
            )
        )
        addRoundRect(
            RoundRect(
                rect = Rect(w * 0.30f, h * 0.65f - pillH / 2f, w * 0.58f, h * 0.65f + pillH / 2f),
                cornerRadius = pillR
            )
        )
    }

    val calWithCutouts = Path().apply {
        op(calBody, pillCutouts, PathOperation.Difference)
    }

    drawLiquidWaveFill(
        shapePath = calWithCutouts,
        progress = progress,
        fillColor = fillColor,
        waveColor = waveColor,
        boundsTop = top,
        boundsBottom = bottom
    )

    val ringTop = h * 0.15f
    val ringBottom = h * 0.28f
    val ringColor = if (progress > 0.5f) fillColor else strokeColor
    drawLine(
        color = ringColor,
        start = Offset(w * 0.35f, ringTop),
        end = Offset(w * 0.35f, ringBottom),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
    drawLine(
        color = ringColor,
        start = Offset(w * 0.65f, ringTop),
        end = Offset(w * 0.65f, ringBottom),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )

    val strokePath = Path().apply {
        addPath(calBody)
        addPath(pillCutouts)
    }
    drawProgressiveStrokePath(
        path = strokePath,
        progress = progress,
        isTransitioning = isTransitioning,
        strokeColor = strokeColor,
        accentGlowColor = waveColor,
        strokeWidth = strokeW
    )
}

/**
 * 5. PROFILE ICON (Person with circular head & full pill-shaped body capsule)
 */
private fun DrawScope.drawProfileAnimatedIcon(
    progress: Float,
    isTransitioning: Boolean,
    strokeColor: Color,
    fillColor: Color,
    waveColor: Color
) {
    val w = size.width
    val h = size.height
    val strokeW = 1.8.dp.toPx()

    val centerX = w * 0.50f
    val headRadius = w * 0.16f
    val headCenterY = h * 0.33f

    val personPath = Path().apply {
        addOval(
            Rect(
                centerX - headRadius,
                headCenterY - headRadius,
                centerX + headRadius,
                headCenterY + headRadius
            )
        )
        // Full pill-shaped shoulder/body outline
        val sLeft = w * 0.20f
        val sRight = w * 0.80f
        val sTop = h * 0.56f
        val sBottom = h * 0.81f
        val pillRadius = (sBottom - sTop) / 2f
        addRoundRect(
            RoundRect(
                rect = Rect(sLeft, sTop, sRight, sBottom),
                cornerRadius = CornerRadius(pillRadius, pillRadius)
            )
        )
    }

    drawLiquidWaveFill(
        shapePath = personPath,
        progress = progress,
        fillColor = fillColor,
        waveColor = waveColor,
        boundsTop = headCenterY - headRadius,
        boundsBottom = h * 0.81f
    )

    drawProgressiveStrokePath(
        path = personPath,
        progress = progress,
        isTransitioning = isTransitioning,
        strokeColor = strokeColor,
        accentGlowColor = waveColor,
        strokeWidth = strokeW
    )
}

/**
 * 6. AI ICON (Curved 4-Point AI Sparkle Orb with pill-shaped inner core & companion sparkle)
 */
private fun DrawScope.drawAiAnimatedIcon(
    progress: Float,
    isTransitioning: Boolean,
    strokeColor: Color,
    fillColor: Color,
    waveColor: Color
) {
    val w = size.width
    val h = size.height
    val strokeW = 1.75.dp.toPx()

    val cx = w * 0.46f
    val cy = h * 0.52f
    val rOuter = w * 0.34f
    val rPinch = w * 0.105f

    // Primary 4-point AI star with smooth concave bezier curves
    val mainStar = Path().apply {
        moveTo(cx, cy - rOuter)
        quadraticTo(cx + rPinch, cy - rPinch, cx + rOuter, cy)
        quadraticTo(cx + rPinch, cy + rPinch, cx, cy + rOuter)
        quadraticTo(cx - rPinch, cy + rPinch, cx - rOuter, cy)
        quadraticTo(cx - rPinch, cy - rPinch, cx, cy - rOuter)
        close()
    }

    // Pill-shaped inner core cutout inside the AI star
    val coreW = w * 0.16f
    val coreH = h * 0.09f
    val coreCorner = CornerRadius(coreH / 2f, coreH / 2f)
    val innerCoreCutout = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(cx - coreW / 2f, cy - coreH / 2f, cx + coreW / 2f, cy + coreH / 2f),
                cornerRadius = coreCorner
            )
        )
    }

    // Companion top-right AI micro-sparkle
    val sx = w * 0.77f
    val sy = h * 0.25f
    val sr = w * 0.13f
    val sp = w * 0.04f
    val subStar = Path().apply {
        moveTo(sx, sy - sr)
        quadraticTo(sx + sp, sy - sp, sx + sr, sy)
        quadraticTo(sx + sp, sy + sp, sx, sy + sr)
        quadraticTo(sx - sp, sy + sp, sx - sr, sy)
        quadraticTo(sx - sp, sy - sp, sx, sy - sr)
        close()
    }

    val starWithCutout = Path().apply {
        op(mainStar, innerCoreCutout, PathOperation.Difference)
        addPath(subStar)
    }

    drawLiquidWaveFill(
        shapePath = starWithCutout,
        progress = progress,
        fillColor = fillColor,
        waveColor = waveColor,
        boundsTop = sy - sr,
        boundsBottom = cy + rOuter
    )

    val combinedStroke = Path().apply {
        addPath(mainStar)
        addPath(innerCoreCutout)
        addPath(subStar)
    }

    drawProgressiveStrokePath(
        path = combinedStroke,
        progress = progress,
        isTransitioning = isTransitioning,
        strokeColor = strokeColor,
        accentGlowColor = waveColor,
        strokeWidth = strokeW
    )
}

