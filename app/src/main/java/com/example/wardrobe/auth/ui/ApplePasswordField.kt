package com.example.wardrobe.auth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import com.example.wardrobe.auth.motion.AuthMotion

@Composable
fun ApplePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Password",
    placeholder: String = "Enter password",
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    testTag: String = "apple_password_input"
) {
    val colors = AppleTheme.colors
    var isFocused by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }

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
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
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
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions
                    )
                }

                // Password Visibility Toggle with Crossfade animation
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { isPasswordVisible = !isPasswordVisible }
                        .testTag("${testTag}_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = isPasswordVisible,
                        animationSpec = tween(AuthMotion.INTERACTION_FAST),
                        label = "visibility_crossfade"
                    ) { visible ->
                        Icon(
                            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (visible) "Hide Password" else "Show Password",
                            tint = if (isFocused) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(tween(AuthMotion.INTERACTION_FAST)),
            exit = fadeOut(tween(AuthMotion.INTERACTION_FAST))
        ) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = AppleTheme.typography.caption.copy(color = colors.destructive),
                    modifier = Modifier.padding(top = 4.dp, start = 6.dp)
                )
            }
        }
    }
}
