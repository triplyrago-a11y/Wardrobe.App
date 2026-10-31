package com.example.wardrobe.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import kotlinx.coroutines.delay

/**
 * Apple Glass Surface
 * Semi-translucent glass with subtle 1dp border, soft shadow, and background blur fallback
 */
@Composable
fun AppleGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AppleSpacing.CardCornerRadius),
    contentColor: Color = AppleTheme.colors.textPrimary,
    border: BorderStroke? = BorderStroke(1.dp, AppleTheme.colors.glassBorder),
    elevation: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    val colors = AppleTheme.colors
    Surface(
        modifier = modifier
            .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.08f))
            .border(border ?: BorderStroke(0.dp, Color.Transparent), shape),
        shape = shape,
        color = colors.glassBg,
        contentColor = contentColor
    ) {
        content()
    }
}

/**
 * Apple Primary and Secondary Buttons with press-scale (0.97) animation
 */
@Composable
fun AppleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    testTag: String = "apple_button"
) {
    val colors = AppleTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "press_scale"
    )

    val bgColor = when {
        isPrimary -> colors.accent
        else -> colors.bgSecondary
    }
    val textColor = when {
        isPrimary -> Color.White
        else -> colors.accent
    }
    val alpha = if (enabled) 1.0f else 0.40f

    Box(
        modifier = modifier
            .testTag(testTag)
            .scale(scale)
            .heightIn(min = AppleSpacing.MinTouchTarget)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = if (isPrimary) alpha else alpha * 0.95f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = textColor,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(AppleSpacing.Space8))
                }
                Text(
                    text = text,
                    style = AppleTheme.typography.body.copy(
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                )
            }
        }
    }
}

/**
 * Apple Pill Chip (36dp visual inside 48dp touch target)
 */
@Composable
fun AppleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "apple_chip"
) {
    val colors = AppleTheme.colors
    val bgColor = if (selected) colors.accent else colors.bgSecondary
    val textColor = if (selected) Color.White else colors.textPrimary

    Box(
        modifier = modifier
            .testTag(testTag)
            .height(AppleSpacing.MinTouchTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(AppleSpacing.ChipHeight)
                .clip(CircleShape)
                .background(bgColor)
                .border(
                    width = 1.dp,
                    color = if (selected) colors.accent else colors.cardBorder,
                    shape = CircleShape
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = AppleTheme.typography.caption.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor
                )
            )
        }
    }
}

/**
 * Apple Input Field with 12dp radius, bgSecondary fill, accent focus ring, inline validation
 */
@Composable
fun AppleInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    testTag: String = "apple_input"
) {
    val colors = AppleTheme.colors
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = AppleTheme.typography.caption.copy(
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                ),
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
            )
        }

        val borderColor = when {
            errorMessage != null -> colors.destructive
            isFocused -> colors.accent
            else -> colors.cardBorder
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppleSpacing.MinTouchTarget)
                .clip(RoundedCornerShape(AppleSpacing.InputCornerRadius))
                .background(colors.bgSecondary)
                .border(
                    width = if (isFocused || errorMessage != null) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(AppleSpacing.InputCornerRadius)
                )
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = AppleTheme.typography.body.copy(color = colors.textSecondary)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                textStyle = AppleTheme.typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = AppleTheme.typography.caption.copy(color = colors.destructive),
                modifier = Modifier.padding(top = 4.dp, start = 6.dp)
            )
        }
    }
}

/**
 * Apple Card Component with 18dp corner radius, 1dp subtle border, and soft shadow
 */
@Composable
fun AppleCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = AppleTheme.colors.bgSecondary,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(AppleSpacing.CardCornerRadius)
    val colors = AppleTheme.colors

    val baseModifier = modifier
        .shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.06f))
        .border(1.dp, colors.cardBorder, shape)
        .clip(shape)
        .background(backgroundColor)

    val finalModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }

    Box(modifier = finalModifier) {
        content()
    }
}

/**
 * Apple Toast Pill (top auto-dismiss)
 */
@Composable
fun AppleToast(
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 2600L
) {
    LaunchedEffect(visible, message) {
        if (visible) {
            delay(durationMs)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        AppleGlassSurface(
            shape = CircleShape,
            elevation = 12.dp,
            modifier = Modifier.padding(top = 24.dp, start = 20.dp, end = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    style = AppleTheme.typography.caption.copy(
                        fontWeight = FontWeight.Medium,
                        color = AppleTheme.colors.textPrimary
                    )
                )
            }
        }
    }
}

/**
 * Apple Confirmation Dialog
 */
@Composable
fun AppleConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppleTheme.colors

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .padding(AppleSpacing.Space24)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AppleGlassSurface(
                shape = RoundedCornerShape(AppleSpacing.CardCornerRadius),
                elevation = 16.dp,
                modifier = Modifier.widthIn(max = 380.dp)
            ) {
                Column(
                    modifier = Modifier.padding(AppleSpacing.Space24),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = AppleTheme.typography.title.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)
                        ),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(AppleSpacing.Space8))
                    Text(
                        text = message,
                        style = AppleTheme.typography.body.copy(
                            color = colors.textSecondary,
                            fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)
                        )
                    )
                    Spacer(modifier = Modifier.height(AppleSpacing.Space24))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
                    ) {
                        AppleButton(
                            text = dismissText,
                            onClick = onDismiss,
                            isPrimary = false,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = AppleSpacing.MinTouchTarget)
                                .clip(CircleShape)
                                .background(if (isDestructive) colors.destructive else colors.accent)
                                .clickable(onClick = onConfirm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = confirmText,
                                style = AppleTheme.typography.body.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
