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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
import com.skilift.app.domain.model.ElevationPoint
import com.skilift.app.domain.model.Leg
import com.skilift.app.ui.theme.BikeGreen
import java.util.Locale

private const val METERS_PER_FOOT = 0.3048
private const val METERS_PER_MILE = 1609.344
private const val MAX_ZOOM = 10f
private val LEFT_PAD = 36.dp

private data class ChartViewport(val zoom: Float = 1f, val startFraction: Float = 0f) {
    val windowSize: Float get() = 1f / zoom
    val endFraction: Float get() = (startFraction + windowSize).coerceAtMost(1f)

    fun fractionAtX(x: Float, leftPad: Float, chartWidth: Float): Float =
        (startFraction + ((x - leftPad) / chartWidth) * windowSize).coerceIn(0f, 1f)
}

@Composable
fun ElevationProfileChart(
    leg: Leg,
    modifier: Modifier = Modifier,
    onFractionSelected: (Float?) -> Unit = {}
) {
    val points = leg.elevationProfile
    if (points.isEmpty()) return

    val totalDist = points.last().distanceMeters

    val greenFill = BikeGreen.copy(alpha = 0.30f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    val useImperial = isImperialLocale()
    val textMeasurer = rememberTextMeasurer()

    var touchX by remember { mutableFloatStateOf(Float.NaN) }
    val viewportState = remember(leg) { mutableStateOf(ChartViewport()) }
    val viewport = viewportState.value

    val visibleElev = visibleElevationRange(
        points, totalDist,
        viewport.startFraction.toDouble(),
        viewport.endFraction.toDouble()
    )
    val visMinElev = visibleElev.first
    val visMaxElev = visibleElev.second
    val visElevRange = (visMaxElev - visMinElev).coerceAtLeast(1.0)

    val minLabel = formatElevation(visMinElev, useImperial)
    val maxLabel = formatElevation(visMaxElev, useImperial)

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
                    .chartGestures(
                        key = leg,
                        viewportState = viewportState,
                        onCursorChange = { touchX = it },
                        onFractionSelected = onFractionSelected
                    )
            ) {
                val leftPad = LEFT_PAD.toPx()
                val bottomPad = 14.dp.toPx()
                val chartWidth = size.width - leftPad
                val chartHeight = size.height - bottomPad

                if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                val distLabelStyle = TextStyle(
                    fontSize = 10.sp,
                    color = labelColor
                )

                val viewStart = viewport.startFraction.toDouble()
                val windowSize = viewport.windowSize.toDouble()

                fun yFor(elev: Double): Float =
                    (chartHeight - ((elev - visMinElev) / visElevRange * chartHeight)).toFloat()

                fun xFor(distFraction: Double): Float {
                    val normalized = ((distFraction - viewStart) / windowSize).toFloat()
                    return leftPad + normalized * chartWidth
                }

                val (linePath, fillPath) = buildElevationPaths(
                    points, totalDist, ::xFor, ::yFor, chartHeight
                )

                clipRect(
                    left = leftPad,
                    top = 0f,
                    right = leftPad + chartWidth,
                    bottom = chartHeight
                ) {
                    drawPath(fillPath, greenFill, style = Fill)
                    drawPath(linePath, BikeGreen, style = Stroke(width = 2.dp.toPx()))

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
                val viewEnd = viewport.endFraction.toDouble()
                val endDist = viewEnd * totalDist
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
                if (viewport.zoom > 1.01f) {
                    val startDist = viewStart * totalDist
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

private fun Modifier.chartGestures(
    key: Any,
    viewportState: MutableState<ChartViewport>,
    onCursorChange: (Float) -> Unit,
    onFractionSelected: (Float?) -> Unit
): Modifier = pointerInput(key) {
    val leftPad = LEFT_PAD.toPx()
    val chartWidth = size.width - leftPad
    if (chartWidth <= 0f) return@pointerInput

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)

        onCursorChange(down.position.x)
        onFractionSelected(viewportState.value.fractionAtX(down.position.x, leftPad, chartWidth))

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
                        onCursorChange(Float.NaN)
                        onFractionSelected(null)
                    }

                    val p0 = pressed[0].position
                    val p1 = pressed[1].position
                    val dist = (p0 - p1).getDistance()

                    if (prevPointerCount >= 2 && prevDist > 1f && dist > 1f) {
                        val vp = viewportState.value
                        val oldCentroidX = (prevP0.x + prevP1.x) / 2f
                        val newCentroidX = (p0.x + p1.x) / 2f
                        val zoomDelta = dist / prevDist

                        val dataAtCentroid = vp.fractionAtX(
                            oldCentroidX, leftPad, chartWidth
                        )

                        val newZoom = (vp.zoom * zoomDelta).coerceIn(1f, MAX_ZOOM)
                        val newWin = 1f / newZoom

                        val newStart = dataAtCentroid -
                            ((newCentroidX - leftPad) / chartWidth) * newWin

                        viewportState.value = ChartViewport(
                            zoom = newZoom,
                            startFraction = newStart.coerceIn(
                                0f, (1f - newWin).coerceAtLeast(0f)
                            )
                        )
                    }

                    prevP0 = p0
                    prevP1 = p1
                    prevDist = dist
                    prevPointerCount = pressed.size
                    event.changes.forEach { it.consume() }
                }

                pressed.size == 1 && !wasTwoFinger -> {
                    onCursorChange(pressed[0].position.x)
                    onFractionSelected(
                        viewportState.value.fractionAtX(pressed[0].position.x, leftPad, chartWidth)
                    )
                    pressed[0].consume()
                    prevPointerCount = 1
                }

                pressed.size == 1 && wasTwoFinger -> {
                    prevPointerCount = 1
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })

        onCursorChange(Float.NaN)
        onFractionSelected(null)
    }
}

private fun buildElevationPaths(
    points: List<ElevationPoint>,
    totalDist: Double,
    xFor: (Double) -> Float,
    yFor: (Double) -> Float,
    chartHeight: Float
): Pair<Path, Path> {
    val linePath = Path()
    val fillPath = Path()

    val firstFrac = points.first().distanceMeters / totalDist
    val firstX = xFor(firstFrac)
    val firstY = yFor(points.first().elevationMeters)

    linePath.moveTo(firstX, firstY)
    fillPath.moveTo(firstX, firstY)

    for (i in 1 until points.size) {
        val frac = points[i].distanceMeters / totalDist
        val x = xFor(frac)
        val y = yFor(points[i].elevationMeters)
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
    }

    val lastFrac = points.last().distanceMeters / totalDist
    fillPath.lineTo(xFor(lastFrac), chartHeight)
    fillPath.lineTo(xFor(firstFrac), chartHeight)
    fillPath.close()

    return linePath to fillPath
}

private fun visibleElevationRange(
    points: List<ElevationPoint>,
    totalDist: Double,
    viewStart: Double,
    viewEnd: Double
): Pair<Double, Double> {
    var min = Double.MAX_VALUE
    var max = Double.MIN_VALUE

    fun consider(elev: Double) {
        if (elev < min) min = elev
        if (elev > max) max = elev
    }

    fun elevAtFraction(f: Double): Double {
        val dist = f * totalDist
        val idx = points.indexOfLast { it.distanceMeters <= dist }
        if (idx < 0) return points.first().elevationMeters
        if (idx >= points.size - 1) return points.last().elevationMeters
        val a = points[idx]
        val b = points[idx + 1]
        val segLen = b.distanceMeters - a.distanceMeters
        if (segLen <= 0) return a.elevationMeters
        val t = (dist - a.distanceMeters) / segLen
        return a.elevationMeters + t * (b.elevationMeters - a.elevationMeters)
    }

    consider(elevAtFraction(viewStart))
    consider(elevAtFraction(viewEnd))

    for (pt in points) {
        val f = pt.distanceMeters / totalDist
        if (f in viewStart..viewEnd) {
            consider(pt.elevationMeters)
        }
    }

    if (min > max) {
        min = points.minOf { it.elevationMeters }
        max = points.maxOf { it.elevationMeters }
    }

    // Add 10% vertical padding so the profile doesn't clamp edge-to-edge
    val padding = (max - min) * 0.1
    min -= padding
    max += padding

    return min to max
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
