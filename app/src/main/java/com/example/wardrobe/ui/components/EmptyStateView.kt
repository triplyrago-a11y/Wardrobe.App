package com.example.wardrobe.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import kotlin.math.roundToInt

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = AppleTheme.colors

    // Subtle floating animation
    val infiniteTransition = rememberInfiniteTransition(label = "floating_symbol")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "symbol_offset"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppleSpacing.Space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Softly floating symbol in secondary tones
        Box(
            modifier = Modifier
                .offset { IntOffset(0, floatOffset.dp.roundToPx()) }
                .size(84.dp)
                .clip(CircleShape)
                .background(colors.bgSecondary),
            contentAlignment = Alignment.Center
        ) {
            WardrobeLogo(
                mode = LogoMode.Symbol,
                size = 54.dp,
                tint = colors.textSecondary.copy(alpha = 0.85f),
                accentTint = colors.accent.copy(alpha = 0.70f),
                includeInteriorFill = true
            )
        }
        Spacer(modifier = Modifier.height(AppleSpacing.Space16))
        Text(
            text = title,
            style = AppleTheme.typography.title.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(AppleSpacing.Space8))
        Text(
            text = subtitle,
            style = AppleTheme.typography.body.copy(
                color = colors.textSecondary,
                fontSize = 15.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AppleSpacing.Space16)
        )
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(AppleSpacing.Space24))
            AppleButton(
                text = actionText,
                onClick = onActionClick,
                isPrimary = true
            )
        }
    }
}
