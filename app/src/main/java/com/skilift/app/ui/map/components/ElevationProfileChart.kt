package com.skilift.app.ui.map.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skilift.app.domain.model.Leg
import com.skilift.app.ui.theme.BikeGreen
import java.util.Locale

private const val METERS_PER_FOOT = 0.3048
private const val METERS_PER_MILE = 1609.344
private const val MAX_ZOOM = 10f

@Composable
fun ElevationProfileChart(
    leg: Leg,
    modifier: Modifier = Modifier,
    onFractionSelected: (Float?) -> Unit = {}
) {
    val points = leg.elevationProfile
    if (points.isEmpty()) return

    val minElev = points.minOf { it.elevationMeters }
    val maxElev = points.maxOf { it.elevationMeters }
    val elevRange = (maxElev - minElev).coerceAtLeast(1.0)
    val totalDist = points.last().distanceMeters

    val greenFill = BikeGreen.copy(alpha = 0.30f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    val useImperial = isImperialLocale()
    val minLabel = formatElevation(minElev, useImperial)
    val maxLabel = formatElevation(maxElev, useImperial)
    val textMeasurer = rememberTextMeasurer()

    // Touch position tracked as raw x pixel within the Canvas; Float.NaN means no touch
    var touchX by remember { mutableFloatStateOf(Float.NaN) }
    // Zoom level (1 = full view) and viewport start as fraction of total distance
    var zoom by remember(leg) { mutableFloatStateOf(1f) }
    var viewStartFraction by remember(leg) { mutableFloatStateOf(0f) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(12.dp)
        ) {
            Text(
                text = maxLabel,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Text(
                text = minLabel,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.align(Alignment.BottomStart)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(leg) {
                        val leftPad = 36.dp.toPx()
                        val chartWidth = size.width - leftPad
                        if (chartWidth <= 0f) return@pointerInput

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            // Show cursor on initial touch
                            touchX = down.position.x
                            val initWindowSize = 1f / zoom
                            val initFrac = viewStartFraction +
                                ((down.position.x - leftPad) / chartWidth) * initWindowSize
                            onFractionSelected(initFrac.coerceIn(0f, 1f))

                            var wasTwoFinger = false
                            var prevP0 = down.position
                            var prevP1 = Offset.Zero
                            var prevDist = 0f
                            var prevPointerCount = 1

                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }

                                when {
                                    pressed.size >= 2 -> {
                                        if (!wasTwoFinger) {
                                            wasTwoFinger = true
                                            touchX = Float.NaN
                                            onFractionSelected(null)
                                        }

                                        val p0 = pressed[0].position
                                        val p1 = pressed[1].position
                                        val dist = (p0 - p1).getDistance()

                                        if (prevPointerCount >= 2 && prevDist > 1f && dist > 1f) {
                                            val oldCentroidX = (prevP0.x + prevP1.x) / 2f
                                            val newCentroidX = (p0.x + p1.x) / 2f
                                            val zoomDelta = dist / prevDist

                                            // Data fraction at the old centroid
                                            val oldWin = 1f / zoom
                                            val dataAtCentroid = viewStartFraction +
                                                ((oldCentroidX - leftPad) / chartWidth) * oldWin

                                            val newZoom = (zoom * zoomDelta).coerceIn(1f, MAX_ZOOM)
                                            val newWin = 1f / newZoom

                                            // Keep centroid data point under the new centroid screen position
                                            val newStart = dataAtCentroid -
                                                ((newCentroidX - leftPad) / chartWidth) * newWin

                                            zoom = newZoom
                                            viewStartFraction = newStart.coerceIn(
                                                0f, (1f - newWin).coerceAtLeast(0f)
                                            )
                                        }

                                        prevP0 = p0
                                        prevP1 = p1
                                        prevDist = dist
                                        prevPointerCount = pressed.size
                                        event.changes.forEach { it.consume() }
                                    }

                                    pressed.size == 1 && !wasTwoFinger -> {
                                        // Single-finger cursor drag
                                        touchX = pressed[0].position.x
                                        val ws = 1f / zoom
                                        val frac = viewStartFraction +
                                            ((pressed[0].position.x - leftPad) / chartWidth) * ws
                                        onFractionSelected(frac.coerceIn(0f, 1f))
                                        pressed[0].consume()
                                        prevPointerCount = 1
                                    }

                                    pressed.size == 1 && wasTwoFinger -> {
                                        // Transitioning from two fingers to one; just update tracking
                                        prevPointerCount = 1
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            touchX = Float.NaN
                            onFractionSelected(null)
                        }
                    }
            ) {
                val leftPad = 36.dp.toPx()
                val bottomPad = 14.dp.toPx()
                val chartWidth = size.width - leftPad
                val chartHeight = size.height - bottomPad

                if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                val windowSize = 1f / zoom
                val viewStart = viewStartFraction.toDouble()

                val distLabelStyle = TextStyle(
                    fontSize = 10.sp,
                    color = labelColor
                )

                fun yFor(elev: Double): Float =
                    (chartHeight - ((elev - minElev) / elevRange * chartHeight)).toFloat()

                fun xFor(distFraction: Double): Float {
                    val normalized = ((distFraction - viewStart) / windowSize).toFloat()
                    return leftPad + normalized * chartWidth
                }

                // Build paths using all points; clipRect handles visibility
                val linePath = Path().apply {
                    val firstFrac = points.first().distanceMeters / totalDist
                    moveTo(xFor(firstFrac), yFor(points.first().elevationMeters))
                    for (i in 1 until points.size) {
                        val frac = points[i].distanceMeters / totalDist
                        lineTo(xFor(frac), yFor(points[i].elevationMeters))
                    }
                }

                val fillPath = Path().apply {
                    val firstFrac = points.first().distanceMeters / totalDist
                    moveTo(xFor(firstFrac), yFor(points.first().elevationMeters))
                    for (i in 1 until points.size) {
                        val frac = points[i].distanceMeters / totalDist
                        lineTo(xFor(frac), yFor(points[i].elevationMeters))
                    }
                    val lastFrac = points.last().distanceMeters / totalDist
                    lineTo(xFor(lastFrac), chartHeight)
                    lineTo(xFor(firstFrac), chartHeight)
                    close()
                }

                // Clip drawing to the chart area so zoomed paths don't overflow
                clipRect(
                    left = leftPad,
                    top = 0f,
                    right = leftPad + chartWidth,
                    bottom = chartHeight
                ) {
                    drawPath(fillPath, greenFill, style = Fill)
                    drawPath(linePath, BikeGreen, style = Stroke(width = 2.dp.toPx()))

                    // Draw vertical cursor line at touch position
                    if (!touchX.isNaN()) {
                        val clampedX = touchX.coerceIn(leftPad, leftPad + chartWidth)
                        drawLine(
                            color = Color.DarkGray,
                            start = Offset(clampedX, 0f),
                            end = Offset(clampedX, chartHeight),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Distance label at visible end
                val viewEnd = (viewStartFraction + windowSize).coerceAtMost(1f)
                val endDist = viewEnd.toDouble() * totalDist
                val endDistLabel = formatDistance(endDist, useImperial)
                val measuredEndLabel = textMeasurer.measure(endDistLabel, distLabelStyle)
                drawText(
                    textLayoutResult = measuredEndLabel,
                    topLeft = Offset(
                        x = leftPad + chartWidth - measuredEndLabel.size.width,
                        y = chartHeight + 2.dp.toPx()
                    )
                )

                // Distance label at visible start when zoomed in
                if (zoom > 1.01f) {
                    val startDist = viewStartFraction.coerceAtLeast(0f).toDouble() * totalDist
                    val startDistLabel = formatDistance(startDist, useImperial)
                    val measuredStartLabel = textMeasurer.measure(startDistLabel, distLabelStyle)
                    drawText(
                        textLayoutResult = measuredStartLabel,
                        topLeft = Offset(
                            x = leftPad,
                            y = chartHeight + 2.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun isImperialLocale(): Boolean {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return locale.country in setOf("US", "MM", "LR")
}

private fun formatElevation(meters: Double, imperial: Boolean): String =
    if (imperial) "%.0f ft".format(meters / METERS_PER_FOOT)
    else "%.0f m".format(meters)

private fun formatDistance(meters: Double, imperial: Boolean): String =
    if (imperial) {
        val miles = meters / METERS_PER_MILE
        if (miles >= 1.0) "%.1f mi".format(miles)
        else "%.0f ft".format(meters / METERS_PER_FOOT)
    } else {
        if (meters >= 1000) "%.1f km".format(meters / 1000.0)
        else "%.0f m".format(meters)
    }
