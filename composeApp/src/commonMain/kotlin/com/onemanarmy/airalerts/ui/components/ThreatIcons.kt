package com.onemanarmy.airalerts.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.onemanarmy.airalerts.domain.model.ThreatType

/**
 * High-contrast Compose Canvas vector icon matching the map marker silhouettes in AlertMapView.
 * Coordinates are defined in a normalized 80x80 grid and scaled cleanly to any Composable size.
 */
@Composable
fun ThreatTypeIcon(
    type: ThreatType,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    rotationDeg: Float = 0f
) {
    Canvas(modifier = modifier) {
        val scale = size.minDimension / 80f
        val offsetX = (size.width - 80f * scale) / 2f
        val offsetY = (size.height - 80f * scale) / 2f

        withTransform({
            translate(left = offsetX, top = offsetY)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            if (rotationDeg != 0f) {
                rotate(degrees = rotationDeg, pivot = Offset(40f, 40f))
            }
        }) {
            drawThreatVector(type = type, isHighlighted = isHighlighted)
        }
    }
}

private fun DrawScope.drawThreatVector(
    type: ThreatType,
    isHighlighted: Boolean
) {
    val cx = 40f
    val cy = 40f

    val whiteOuterStroke = Stroke(
        width = 6.5f,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )
    val blackFill = Color(0xFF080A0E)
    val detailAccent = if (isHighlighted) Color(0xFFFF2D2D) else Color.White
    val detailWhite = Color.White

    when (type) {
        ThreatType.UAV -> {
            // Shahed-136: cropped delta wing with wingtip stabilizer endplates and 2-blade pusher propeller.
            val bodyPath = Path().apply {
                moveTo(cx, 10f)
                lineTo(cx + 3.2f, 20f)
                lineTo(63f, 48f)
                lineTo(63.5f, 58f)
                lineTo(60.5f, 58f)
                lineTo(59f, 55f)
                lineTo(cx + 4f, 55f)
                lineTo(cx + 3.2f, 59.5f)
                lineTo(cx - 3.2f, 59.5f)
                lineTo(cx - 4f, 55f)
                lineTo(21f, 55f)
                lineTo(19.5f, 58f)
                lineTo(16.5f, 58f)
                lineTo(17f, 48f)
                lineTo(cx - 3.2f, 20f)
                close()
            }
            // Pass 1: White outer border for wing + propeller
            drawPath(bodyPath, color = Color.White, style = whiteOuterStroke)
            drawLine(
                color = Color.White,
                start = Offset(cx, 59f),
                end = Offset(cx, 64.5f),
                strokeWidth = 7.0f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(cx - 10.5f, 64.5f),
                end = Offset(cx + 10.5f, 64.5f),
                strokeWidth = 7.5f,
                cap = StrokeCap.Round
            )

            // Pass 2: Jet-black silhouette fill
            drawPath(bodyPath, color = blackFill)
            drawLine(
                color = blackFill,
                start = Offset(cx, 59f),
                end = Offset(cx, 64.5f),
                strokeWidth = 2.6f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = blackFill,
                start = Offset(cx - 10.5f, 64.5f),
                end = Offset(cx + 10.5f, 64.5f),
                strokeWidth = 3.0f,
                cap = StrokeCap.Round
            )

            // Pass 3: Crisp internal spine, wingtip endplate seams & prop hub
            drawLine(
                color = detailWhite,
                start = Offset(cx, 19f),
                end = Offset(cx, 52f),
                strokeWidth = 1.8f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = detailWhite,
                start = Offset(19.5f, 48.5f),
                end = Offset(19.5f, 55.5f),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = detailWhite,
                start = Offset(60.5f, 48.5f),
                end = Offset(60.5f, 55.5f),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )
            drawCircle(color = detailWhite, radius = 1.7f, center = Offset(cx, 64.5f))
            if (isHighlighted) {
                drawCircle(color = detailAccent, radius = 3.0f, center = Offset(cx, 38f))
            }
        }

        ThreatType.JET_SHAHED -> {
            // Shahed-238 (Jet Shahed): cropped delta wing with dorsal air intake and rear turbojet exhaust flame.
            val bodyPath = Path().apply {
                moveTo(cx, 10f)
                lineTo(cx + 3.2f, 20f)
                lineTo(63f, 48f)
                lineTo(63.5f, 58f)
                lineTo(60.5f, 58f)
                lineTo(59f, 55f)
                lineTo(cx + 4.4f, 55f)
                lineTo(cx + 3f, 60.5f)
                lineTo(cx - 3f, 60.5f)
                lineTo(cx - 4.4f, 55f)
                lineTo(21f, 55f)
                lineTo(19.5f, 58f)
                lineTo(16.5f, 58f)
                lineTo(17f, 48f)
                lineTo(cx - 3.2f, 20f)
                close()
            }
            val flameOuterPath = Path().apply {
                moveTo(cx - 3.6f, 60.5f)
                lineTo(cx, 72.5f)
                lineTo(cx + 3.6f, 60.5f)
                close()
            }
            val flameInnerPath = Path().apply {
                moveTo(cx - 1.8f, 60.5f)
                lineTo(cx, 68f)
                lineTo(cx + 1.8f, 60.5f)
                close()
            }
            val intakePath = Path().apply {
                moveTo(cx, 25.5f)
                lineTo(cx + 3.2f, 31f)
                lineTo(cx + 2.4f, 38.5f)
                lineTo(cx - 2.4f, 38.5f)
                lineTo(cx - 3.2f, 31f)
                close()
            }

            // Pass 1: White outer border around jet flame & airframe
            drawPath(flameOuterPath, color = Color.White, style = whiteOuterStroke)
            drawPath(bodyPath, color = Color.White, style = whiteOuterStroke)

            // Pass 2: Turbojet exhaust flame + black airframe silhouette
            drawPath(flameOuterPath, color = Color(0xFFFF5722))
            drawPath(flameInnerPath, color = Color(0xFFFFC107))
            drawPath(bodyPath, color = blackFill)

            // Pass 3: Dorsal air intake scoop + wingtip endplate seams
            drawPath(intakePath, color = detailWhite)
            drawLine(
                color = detailWhite,
                start = Offset(19.5f, 48.5f),
                end = Offset(19.5f, 55.5f),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = detailWhite,
                start = Offset(60.5f, 48.5f),
                end = Offset(60.5f, 55.5f),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )
            if (isHighlighted) {
                drawCircle(color = detailAccent, radius = 3.0f, center = Offset(cx, 46.5f))
            }
        }

        ThreatType.FPV -> {
            val rPos = 17f
            val rotorRadius = 9.5f
            val bodyPath = Path().apply {
                moveTo(cx - 5.5f, cy - 13f)
                lineTo(cx + 5.5f, cy - 13f)
                lineTo(cx + 6.5f, cy + 12f)
                lineTo(cx - 6.5f, cy + 12f)
                close()
            }

            // Pass 1: White outer border for arms, rotors, and body
            drawLine(
                color = Color.White,
                start = Offset(cx - rPos, cy - rPos),
                end = Offset(cx + rPos, cy + rPos),
                strokeWidth = 10.5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(cx + rPos, cy - rPos),
                end = Offset(cx - rPos, cy + rPos),
                strokeWidth = 10.5f,
                cap = StrokeCap.Round
            )
            drawCircle(color = Color.White, radius = rotorRadius, center = Offset(cx - rPos, cy - rPos), style = whiteOuterStroke)
            drawCircle(color = Color.White, radius = rotorRadius, center = Offset(cx + rPos, cy - rPos), style = whiteOuterStroke)
            drawCircle(color = Color.White, radius = rotorRadius, center = Offset(cx - rPos, cy + rPos), style = whiteOuterStroke)
            drawCircle(color = Color.White, radius = rotorRadius, center = Offset(cx + rPos, cy + rPos), style = whiteOuterStroke)
            drawPath(bodyPath, color = Color.White, style = whiteOuterStroke)

            // Pass 2: Jet-black silhouette fill
            drawLine(
                color = blackFill,
                start = Offset(cx - rPos, cy - rPos),
                end = Offset(cx + rPos, cy + rPos),
                strokeWidth = 4.6f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = blackFill,
                start = Offset(cx + rPos, cy - rPos),
                end = Offset(cx - rPos, cy + rPos),
                strokeWidth = 4.6f,
                cap = StrokeCap.Round
            )
            drawCircle(color = blackFill, radius = rotorRadius, center = Offset(cx - rPos, cy - rPos))
            drawCircle(color = blackFill, radius = rotorRadius, center = Offset(cx + rPos, cy - rPos))
            drawCircle(color = blackFill, radius = rotorRadius, center = Offset(cx - rPos, cy + rPos))
            drawCircle(color = blackFill, radius = rotorRadius, center = Offset(cx + rPos, cy + rPos))
            drawPath(bodyPath, color = blackFill)

            drawCircle(color = detailWhite, radius = 2.5f, center = Offset(cx - rPos, cy - rPos))
            drawCircle(color = detailWhite, radius = 2.5f, center = Offset(cx + rPos, cy - rPos))
            drawCircle(color = detailWhite, radius = 2.5f, center = Offset(cx - rPos, cy + rPos))
            drawCircle(color = detailWhite, radius = 2.5f, center = Offset(cx + rPos, cy + rPos))
            drawCircle(color = detailAccent, radius = 2.6f, center = Offset(cx, cy - 8f))
        }

        ThreatType.RECON -> {
            val path = Path().apply {
                moveTo(cx, 13f)
                lineTo(cx + 3.5f, 28f)
                lineTo(68f, 33f)
                lineTo(68f, 40f)
                lineTo(cx + 3.5f, 39f)
                lineTo(cx + 3f, 56f)
                lineTo(cx + 12f, 64f)
                lineTo(cx + 10f, 67f)
                lineTo(cx, 62f)
                lineTo(cx - 10f, 67f)
                lineTo(cx - 12f, 64f)
                lineTo(cx - 3f, 56f)
                lineTo(cx - 3.5f, 39f)
                lineTo(12f, 40f)
                lineTo(12f, 33f)
                lineTo(cx - 3.5f, 28f)
                close()
            }
            drawPath(path, color = Color.White, style = whiteOuterStroke)
            drawPath(path, color = blackFill)
            drawCircle(color = detailAccent, radius = 2.4f, center = Offset(cx, 25f))
        }

        ThreatType.CRUISE_MISSILE -> {
            val path = Path().apply {
                moveTo(cx, 10f)
                lineTo(cx + 4.8f, 21f)
                lineTo(cx + 4.8f, 33f)
                lineTo(64f, 44f)
                lineTo(64f, 49f)
                lineTo(cx + 4.8f, 43f)
                lineTo(cx + 4.8f, 57f)
                lineTo(cx + 13f, 64f)
                lineTo(cx + 13f, 67f)
                lineTo(cx + 3.5f, 64f)
                lineTo(cx, 66f)
                lineTo(cx - 3.5f, 64f)
                lineTo(cx - 13f, 67f)
                lineTo(cx - 13f, 64f)
                lineTo(cx - 4.8f, 57f)
                lineTo(cx - 4.8f, 43f)
                lineTo(16f, 49f)
                lineTo(16f, 44f)
                lineTo(cx - 4.8f, 33f)
                lineTo(cx - 4.8f, 21f)
                close()
            }
            drawPath(path, color = Color.White, style = whiteOuterStroke)
            drawPath(path, color = blackFill)
            drawLine(
                color = detailWhite,
                start = Offset(cx, 22f),
                end = Offset(cx, 55f),
                strokeWidth = 2.0f,
                cap = StrokeCap.Round
            )
        }

        ThreatType.BALLISTIC -> {
            val path = Path().apply {
                moveTo(cx, 10f)
                lineTo(cx + 7.2f, 25f)
                lineTo(cx + 7.2f, 50f)
                lineTo(cx + 16f, 62f)
                lineTo(cx + 16f, 66f)
                lineTo(cx + 6.5f, 63f)
                lineTo(cx + 5f, 67f)
                lineTo(cx - 5f, 67f)
                lineTo(cx - 6.5f, 63f)
                lineTo(cx - 16f, 66f)
                lineTo(cx - 16f, 62f)
                lineTo(cx - 7.2f, 50f)
                lineTo(cx - 7.2f, 25f)
                close()
            }
            drawPath(path, color = Color.White, style = whiteOuterStroke)
            drawPath(path, color = blackFill)
            drawLine(
                color = detailWhite,
                start = Offset(cx - 5.5f, 29f),
                end = Offset(cx + 5.5f, 29f),
                strokeWidth = 2.0f,
                cap = StrokeCap.Round
            )
            drawCircle(color = detailAccent, radius = 3.0f, center = Offset(cx, 44f))
        }

        ThreatType.KAB -> {
            val path = Path().apply {
                moveTo(cx, 14f)
                lineTo(cx + 6.5f, 22f)
                lineTo(cx + 6.5f, 31f)
                lineTo(63f, 41f)
                lineTo(61f, 46f)
                lineTo(cx + 6.5f, 42f)
                lineTo(cx + 6f, 55f)
                lineTo(cx + 12f, 62f)
                lineTo(cx + 10f, 65f)
                lineTo(cx, 62f)
                lineTo(cx - 10f, 65f)
                lineTo(cx - 12f, 62f)
                lineTo(cx - 6f, 55f)
                lineTo(cx - 6.5f, 42f)
                lineTo(19f, 46f)
                lineTo(17f, 41f)
                lineTo(cx - 6.5f, 31f)
                lineTo(cx - 6.5f, 22f)
                close()
            }
            drawPath(path, color = Color.White, style = whiteOuterStroke)
            drawPath(path, color = blackFill)
            drawCircle(color = detailAccent, radius = 2.8f, center = Offset(cx, 28f))
        }

        ThreatType.AVIATION -> {
            val path = Path().apply {
                moveTo(cx, 11f)
                lineTo(cx + 4.5f, 24f)
                lineTo(cx + 7.5f, 32f)
                lineTo(67f, 47f)
                lineTo(65f, 52f)
                lineTo(cx + 7f, 47f)
                lineTo(cx + 6f, 55f)
                lineTo(cx + 16f, 63f)
                lineTo(cx + 14f, 67f)
                lineTo(cx + 4.5f, 63f)
                lineTo(cx + 2.5f, 66f)
                lineTo(cx, 63f)
                lineTo(cx - 2.5f, 66f)
                lineTo(cx - 4.5f, 63f)
                lineTo(cx - 14f, 67f)
                lineTo(cx - 16f, 63f)
                lineTo(cx - 6f, 55f)
                lineTo(cx - 7f, 47f)
                lineTo(15f, 52f)
                lineTo(13f, 47f)
                lineTo(cx - 7.5f, 32f)
                lineTo(cx - 4.5f, 24f)
                close()
            }
            drawPath(path, color = Color.White, style = whiteOuterStroke)
            drawPath(path, color = blackFill)
            drawLine(
                color = detailWhite,
                start = Offset(cx, 19f),
                end = Offset(cx, 28f),
                strokeWidth = 2.0f,
                cap = StrokeCap.Round
            )
        }

        ThreatType.UNKNOWN -> {
            val path = Path().apply {
                moveTo(cx, 15f)
                lineTo(65f, cy)
                lineTo(cx, 65f)
                lineTo(15f, cy)
                close()
            }
            drawPath(path, color = Color.White, style = whiteOuterStroke)
            drawPath(path, color = blackFill)
            drawCircle(color = detailAccent, radius = 4.5f, center = Offset(cx, cy))
        }
    }
}
