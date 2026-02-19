package com.skilift.app.ui.map.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skilift.app.domain.model.ElevationPoint
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.TransportMode
import java.util.Locale

private const val GAP_DP = 12f
private const val METERS_PER_FOOT = 0.3048
private const val METERS_PER_MILE = 1609.344

@Composable
fun ElevationProfileChart(
    itinerary: Itinerary,
    modifier: Modifier = Modifier
) {
    val segments = buildBikeSegments(itinerary)
    if (segments.isEmpty()) return

    val allPoints = segments.flatMap { it }
    val minElev = allPoints.minOf { it.elevationMeters }
    val maxElev = allPoints.maxOf { it.elevationMeters }
    val elevRange = (maxElev - minElev).coerceAtLeast(1.0)
    val totalBikeDist = segments.sumOf { it.last().distanceMeters }

    val green = Color(0xFF4CAF50)
    val greenFill = green.copy(alpha = 0.30f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    val useImperial = isImperialLocale()
    val minLabel = formatElevation(minElev, useImperial)
    val maxLabel = formatElevation(maxElev, useImperial)
    val textMeasurer = rememberTextMeasurer()

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

            Canvas(modifier = Modifier.fillMaxSize()) {
                val leftPad = 36.dp.toPx()
                val bottomPad = 14.dp.toPx()
                val chartWidth = size.width - leftPad
                val chartHeight = size.height - bottomPad

                if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                val gapCount = (segments.size - 1).coerceAtLeast(0)
                val totalGapPx = gapCount * GAP_DP.dp.toPx()
                val drawableWidth = (chartWidth - totalGapPx).coerceAtLeast(1f)

                val distLabelStyle = TextStyle(
                    fontSize = 10.sp,
                    color = labelColor
                )

                fun yFor(elev: Double): Float =
                    (chartHeight - ((elev - minElev) / elevRange * chartHeight)).toFloat()

                var xOffset = leftPad
                for ((segIndex, segment) in segments.withIndex()) {
                    val segDist = segment.last().distanceMeters
                    val segWidthPx = if (totalBikeDist > 0)
                        (segDist / totalBikeDist * drawableWidth).toFloat()
                    else drawableWidth

                    fun xFor(dist: Double): Float =
                        xOffset + if (segDist > 0) (dist / segDist * segWidthPx).toFloat() else 0f

                    val linePath = Path().apply {
                        moveTo(xFor(segment.first().distanceMeters), yFor(segment.first().elevationMeters))
                        for (i in 1 until segment.size) {
                            lineTo(xFor(segment[i].distanceMeters), yFor(segment[i].elevationMeters))
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(xFor(segment.last().distanceMeters), chartHeight)
                        lineTo(xFor(segment.first().distanceMeters), chartHeight)
                        close()
                    }

                    drawPath(fillPath, greenFill, style = Fill)
                    drawPath(linePath, green, style = Stroke(width = 2.dp.toPx()))

                    val segLabel = formatDistance(segDist, useImperial)
                    val measuredLabel = textMeasurer.measure(segLabel, distLabelStyle)
                    drawText(
                        textLayoutResult = measuredLabel,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            x = xOffset + segWidthPx - measuredLabel.size.width,
                            y = chartHeight + 2.dp.toPx()
                        )
                    )

                    xOffset += segWidthPx
                    if (segIndex < segments.size - 1) {
                        xOffset += GAP_DP.dp.toPx()
                    }
                }
            }
        }
    }
}

private fun buildBikeSegments(itinerary: Itinerary): List<List<ElevationPoint>> {
    return itinerary.legs
        .filter { it.mode == TransportMode.BICYCLE && it.elevationProfile.isNotEmpty() }
        .map { it.elevationProfile }
}

fun Itinerary.hasBikingElevationData(): Boolean =
    legs.any { it.mode == TransportMode.BICYCLE && it.elevationProfile.isNotEmpty() }

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
