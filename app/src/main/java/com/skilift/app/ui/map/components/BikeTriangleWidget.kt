package com.skilift.app.ui.map.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

data class TriangleWeights(
    val time: Float,
    val safety: Float,
    val flatness: Float
)

@Composable
fun BikeTriangleWidget(
    weights: TriangleWeights,
    onWeightsChanged: (TriangleWeights) -> Unit,
    onWeightsChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val tri = triangleVertices(w, h)
                                val newWeights = offsetToWeights(offset, tri)
                                if (newWeights != null) {
                                    onWeightsChanged(newWeights)
                                    onWeightsChangeFinished()
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    val tri = triangleVertices(w, h)
                                    val newWeights = offsetToWeights(offset, tri)
                                    if (newWeights != null) {
                                        onWeightsChanged(newWeights)
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    val tri = triangleVertices(w, h)
                                    val newWeights = offsetToWeights(change.position, tri)
                                    if (newWeights != null) {
                                        onWeightsChanged(newWeights)
                                    }
                                },
                                onDragEnd = {
                                    onWeightsChangeFinished()
                                },
                                onDragCancel = {
                                    onWeightsChangeFinished()
                                }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val tri = triangleVertices(w, h)

                    // Draw filled triangle background
                    val trianglePath = Path().apply {
                        moveTo(tri.top.x, tri.top.y)
                        lineTo(tri.bottomLeft.x, tri.bottomLeft.y)
                        lineTo(tri.bottomRight.x, tri.bottomRight.y)
                        close()
                    }
                    drawPath(trianglePath, surfaceVariant, style = Fill)
                    drawPath(trianglePath, onSurface.copy(alpha = 0.3f), style = Stroke(width = 2f))

                    // Draw vertex labels
                    val labelSize = with(density) { 12.dp.toPx() }
                    drawVertexLabel("Fast", tri.top, labelSize, onSurface, yOffset = -labelSize)
                    drawVertexLabel("Safe", tri.bottomLeft, labelSize, onSurface, yOffset = labelSize * 1.2f)
                    drawVertexLabel("Flat", tri.bottomRight, labelSize, onSurface, yOffset = labelSize * 1.2f)

                    // Draw the user's position
                    val pointPos = weightsToOffset(weights, tri)
                    drawCircle(
                        color = primaryColor,
                        radius = 12f,
                        center = pointPos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 12f,
                        center = pointPos,
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
    }
}

private data class TriangleVertices(
    val top: Offset,
    val bottomLeft: Offset,
    val bottomRight: Offset
)

private fun triangleVertices(width: Float, height: Float): TriangleVertices {
    val padding = 32f
    val triHeight = height - padding * 2
    val triWidth = triHeight * 2f / sqrt(3f)

    val centerX = width / 2f
    val top = Offset(centerX, padding)
    val bottomLeft = Offset(centerX - triWidth / 2f, padding + triHeight)
    val bottomRight = Offset(centerX + triWidth / 2f, padding + triHeight)

    return TriangleVertices(top, bottomLeft, bottomRight)
}

/**
 * Convert a point to barycentric weights, clamped to the triangle.
 * Top vertex = Time, Bottom-left = Safety, Bottom-right = Flatness.
 */
private fun offsetToWeights(offset: Offset, tri: TriangleVertices): TriangleWeights? {
    val (v0, v1, v2) = Triple(tri.top, tri.bottomLeft, tri.bottomRight)

    val denom = (v1.y - v2.y) * (v0.x - v2.x) + (v2.x - v1.x) * (v0.y - v2.y)
    if (denom == 0f) return null

    var wTime = ((v1.y - v2.y) * (offset.x - v2.x) + (v2.x - v1.x) * (offset.y - v2.y)) / denom
    var wSafety = ((v2.y - v0.y) * (offset.x - v2.x) + (v0.x - v2.x) * (offset.y - v2.y)) / denom
    var wFlatness = 1f - wTime - wSafety

    // Clamp to triangle bounds and normalize
    wTime = wTime.coerceIn(0f, 1f)
    wSafety = wSafety.coerceIn(0f, 1f)
    wFlatness = wFlatness.coerceIn(0f, 1f)

    val sum = wTime + wSafety + wFlatness
    if (sum == 0f) return TriangleWeights(1f / 3f, 1f / 3f, 1f / 3f)

    return TriangleWeights(
        time = wTime / sum,
        safety = wSafety / sum,
        flatness = wFlatness / sum
    )
}

/**
 * Convert barycentric weights back to a pixel offset inside the triangle.
 */
private fun weightsToOffset(weights: TriangleWeights, tri: TriangleVertices): Offset {
    val x = weights.time * tri.top.x + weights.safety * tri.bottomLeft.x + weights.flatness * tri.bottomRight.x
    val y = weights.time * tri.top.y + weights.safety * tri.bottomLeft.y + weights.flatness * tri.bottomRight.y
    return Offset(x, y)
}

private fun DrawScope.drawVertexLabel(
    text: String,
    position: Offset,
    textSize: Float,
    color: Color,
    yOffset: Float = 0f
) {
    val paint = android.graphics.Paint().apply {
        this.textSize = textSize
        this.color = color.toArgb()
        this.textAlign = android.graphics.Paint.Align.CENTER
        this.isAntiAlias = true
        this.typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(
        text,
        position.x,
        position.y + yOffset,
        paint
    )
}
