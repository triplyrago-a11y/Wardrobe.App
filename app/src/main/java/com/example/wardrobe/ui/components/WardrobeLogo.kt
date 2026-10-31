package com.example.wardrobe.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppleTheme

enum class LogoMode {
    Symbol,
    Lockup,
    SparkleOnly
}

object LogoPaths {
    // Exact official SVG path coordinates in 512x512 space
    const val VIEWPORT_SIZE = 512f

    fun createBodyPath(): Path = Path().apply {
        moveTo(92f, 368f)
        lineTo(256f, 252f)
        lineTo(420f, 368f)
        close()
    }

    fun createHookPath(): Path = Path().apply {
        moveTo(256f, 252f)
        lineTo(256f, 224f)
        cubicTo(256f, 198f, 278f, 180f, 304f, 180f)
        cubicTo(330f, 180f, 350f, 198f, 350f, 224f)
    }

    fun createSparklePath(): Path = Path().apply {
        moveTo(160f, 138f)
        quadraticTo(168f, 184f, 214f, 192f)
        quadraticTo(168f, 200f, 160f, 246f)
        quadraticTo(152f, 200f, 106f, 192f)
        quadraticTo(152f, 184f, 160f, 138f)
        close()
    }
}

/**
 * Reusable WardrobeLogo Composable
 * Draws the official single-stroke clothes hanger with AI sparkle.
 * Theme-aware, crisp, and resolution-independent.
 */
@Composable
fun WardrobeLogo(
    mode: LogoMode = LogoMode.Symbol,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color? = null,
    accentTint: Color? = null,
    includeInteriorFill: Boolean = true,
    fontSize: Float = 22f
) {
    val colors = AppleTheme.colors
    val strokeColor = tint ?: colors.textPrimary
    val sparkleColor = accentTint ?: (if (tint != null) tint else colors.accent)
    val interiorFillColor = if (colors.isDark) {
        colors.accent.copy(alpha = 0.22f)
    } else {
        colors.accent.copy(alpha = 0.14f)
    }

    val bodyPath = remember { LogoPaths.createBodyPath() }
    val hookPath = remember { LogoPaths.createHookPath() }
    val sparklePath = remember { LogoPaths.createSparklePath() }

    when (mode) {
        LogoMode.Symbol -> {
            Canvas(modifier = modifier.size(size)) {
                drawSymbol(
                    bodyPath = bodyPath,
                    hookPath = hookPath,
                    sparklePath = sparklePath,
                    strokeColor = strokeColor,
                    sparkleColor = sparkleColor,
                    fillColor = if (includeInteriorFill) interiorFillColor else Color.Transparent
                )
            }
        }
        LogoMode.SparkleOnly -> {
            Canvas(modifier = modifier.size(size)) {
                val scale = size.toPx() / 150f
                withTransform({
                    scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
                    translate(-90f, -120f)
                }) {
                    drawPath(sparklePath, color = sparkleColor)
                }
            }
        }
        LogoMode.Lockup -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Canvas(modifier = Modifier.size(size)) {
                    drawSymbol(
                        bodyPath = bodyPath,
                        hookPath = hookPath,
                        sparklePath = sparklePath,
                        strokeColor = strokeColor,
                        sparkleColor = sparkleColor,
                        fillColor = if (includeInteriorFill) interiorFillColor else Color.Transparent
                    )
                }

                Spacer(modifier = Modifier.width(size * 0.28f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Wardrobe",
                        style = AppleTheme.typography.title.copy(
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = strokeColor,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    Text(
                        text = ".AI",
                        style = AppleTheme.typography.title.copy(
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = sparkleColor,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawSymbol(
    bodyPath: Path,
    hookPath: Path,
    sparklePath: Path,
    strokeColor: Color,
    sparkleColor: Color,
    fillColor: Color
) {
    val scale = size.minDimension / LogoPaths.VIEWPORT_SIZE
    val strokeWidth = 26f * scale

    withTransform({
        scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
    }) {
        // Interior soft accent fill
        if (fillColor != Color.Transparent) {
            drawPath(path = bodyPath, color = fillColor)
        }

        // Hanger body stroke
        drawPath(
            path = bodyPath,
            color = strokeColor,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Hanger hook stroke
        drawPath(
            path = hookPath,
            color = strokeColor,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Sparkle fill
        drawPath(
            path = sparklePath,
            color = sparkleColor
        )
    }
}
