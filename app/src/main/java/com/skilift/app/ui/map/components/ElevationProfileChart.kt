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
import androidx.compose.ui.unit.dp
import com.skilift.app.domain.model.ElevationPoint
import com.skilift.app.domain.model.Itinerary
import com.skilift.app.domain.model.TransportMode

@Composable
fun ElevationProfileChart(
    itinerary: Itinerary,
    modifier: Modifier = Modifier
) {
    val points = buildCombinedProfile(itinerary)
    if (points.isEmpty()) return

    val green = Color(0xFF4CAF50)
    val greenFill = green.copy(alpha = 0.30f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

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
            val minElev = points.minOf { it.elevationMeters }
            val maxElev = points.maxOf { it.elevationMeters }
            val elevRange = (maxElev - minElev).coerceAtLeast(1.0)
            val totalDist = points.last().distanceMeters

            // Labels
            val minLabel = "%.0f m".format(minElev)
            val maxLabel = "%.0f m".format(maxElev)
            val distLabel = if (totalDist >= 1000) "%.1f km".format(totalDist / 1000.0)
            else "%.0f m".format(totalDist)

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
            Text(
                text = distLabel,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.align(Alignment.BottomEnd)
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val leftPad = 36.dp.toPx()
                val bottomPad = 14.dp.toPx()
                val chartWidth = size.width - leftPad
                val chartHeight = size.height - bottomPad

                if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                fun xFor(dist: Double): Float =
                    leftPad + (dist / totalDist * chartWidth).toFloat()

                fun yFor(elev: Double): Float =
                    (chartHeight - ((elev - minElev) / elevRange * chartHeight)).toFloat()

                val linePath = Path().apply {
                    moveTo(xFor(points.first().distanceMeters), yFor(points.first().elevationMeters))
                    for (i in 1 until points.size) {
                        lineTo(xFor(points[i].distanceMeters), yFor(points[i].elevationMeters))
                    }
                }

                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(xFor(points.last().distanceMeters), chartHeight)
                    lineTo(xFor(points.first().distanceMeters), chartHeight)
                    close()
                }

                drawPath(fillPath, greenFill, style = Fill)
                drawPath(linePath, green, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

private fun buildCombinedProfile(itinerary: Itinerary): List<ElevationPoint> {
    val result = mutableListOf<ElevationPoint>()
    var cumulativeDistance = 0.0

    for (leg in itinerary.legs) {
        if (leg.mode != TransportMode.BICYCLE || leg.elevationProfile.isEmpty()) {
            cumulativeDistance += leg.distanceMeters
            continue
        }

        for (point in leg.elevationProfile) {
            result.add(
                ElevationPoint(
                    distanceMeters = cumulativeDistance + point.distanceMeters,
                    elevationMeters = point.elevationMeters
                )
            )
        }
        cumulativeDistance += leg.distanceMeters
    }
    return result
}

fun Itinerary.hasBikingElevationData(): Boolean =
    legs.any { it.mode == TransportMode.BICYCLE && it.elevationProfile.isNotEmpty() }
