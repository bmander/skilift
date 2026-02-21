package com.skilift.app.ui.map.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
    val distLabel = formatDistance(totalDist, useImperial)
    val textMeasurer = rememberTextMeasurer()

    // Touch position tracked as raw x pixel within the Canvas; Float.NaN means no touch
    var touchX by remember { mutableFloatStateOf(Float.NaN) }

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
                        detectDragGestures(
                            onDrag = { change, _ ->
                                change.consume()
                                touchX = change.position.x
                                val leftPad = 36.dp.toPx()
                                val chartWidth = size.width - leftPad
                                if (chartWidth > 0f) {
                                    onFractionSelected(fractionAtX(change.position.x, leftPad, chartWidth))
                                }
                            },
                            onDragEnd = {
                                touchX = Float.NaN
                                onFractionSelected(null)
                            },
                            onDragCancel = {
                                touchX = Float.NaN
                                onFractionSelected(null)
                            }
                        )
                    }
                    .pointerInput(leg) {
                        detectTapGestures(
                            onPress = { offset ->
                                touchX = offset.x
                                val leftPad = 36.dp.toPx()
                                val chartWidth = size.width - leftPad
                                if (chartWidth > 0f) {
                                    onFractionSelected(fractionAtX(offset.x, leftPad, chartWidth))
                                }
                                tryAwaitRelease()
                                touchX = Float.NaN
                                onFractionSelected(null)
                            }
                        )
                    }
            ) {
                val leftPad = 36.dp.toPx()
                val bottomPad = 14.dp.toPx()
                val chartWidth = size.width - leftPad
                val chartHeight = size.height - bottomPad

                if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                val distLabelStyle = TextStyle(
                    fontSize = 10.sp,
                    color = labelColor
                )

                fun yFor(elev: Double): Float =
                    (chartHeight - ((elev - minElev) / elevRange * chartHeight)).toFloat()

                fun xFor(dist: Double): Float =
                    leftPad + if (totalDist > 0) (dist / totalDist * chartWidth).toFloat() else 0f

                val linePath = Path().apply {
                    moveTo(xFor(points.first().distanceMeters), yFor(points.first().elevationMeters))
                    for (i in 1 until points.size) {
                        lineTo(xFor(points[i].distanceMeters), yFor(points[i].elevationMeters))
                    }
                }

                val fillPath = Path().apply {
                    moveTo(xFor(points.first().distanceMeters), yFor(points.first().elevationMeters))
                    for (i in 1 until points.size) {
                        lineTo(xFor(points[i].distanceMeters), yFor(points[i].elevationMeters))
                    }
                    lineTo(xFor(points.last().distanceMeters), chartHeight)
                    lineTo(xFor(points.first().distanceMeters), chartHeight)
                    close()
                }

                drawPath(fillPath, greenFill, style = Fill)
                drawPath(linePath, BikeGreen, style = Stroke(width = 2.dp.toPx()))

                val measuredLabel = textMeasurer.measure(distLabel, distLabelStyle)
                drawText(
                    textLayoutResult = measuredLabel,
                    topLeft = Offset(
                        x = leftPad + chartWidth - measuredLabel.size.width,
                        y = chartHeight + 2.dp.toPx()
                    )
                )

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
        }
    }
}

/** Convert a raw x pixel to a 0.0–1.0 fraction across the chart drawing area. */
private fun fractionAtX(x: Float, leftPad: Float, chartWidth: Float): Float =
    ((x - leftPad) / chartWidth).coerceIn(0f, 1f)

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
