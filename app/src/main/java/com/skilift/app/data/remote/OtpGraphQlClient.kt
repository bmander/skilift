package com.skilift.app.data.remote

import com.skilift.app.data.remote.dto.OtpPlanConnectionResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Named

class OtpGraphQlClient @Inject constructor(
    private val httpClient: HttpClient,
    @Named("otpBaseUrl") private val baseUrl: String
) {
    suspend fun planConnection(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        bicycleReluctance: Double = 2.0,
        bicycleBoardCost: Int = 600,
        bicycleSpeed: Double = 5.0,
        triangleTimeFactor: Double = 0.3,
        triangleSafetyFactor: Double = 0.4,
        triangleFlatnessFactor: Double = 0.3,
        hillReluctance: Double = 1.0,
        numItineraries: Int = 5,
        dateTime: String? = null,
        arriveBy: Boolean = false
    ): OtpPlanConnectionResponse {
        val query = buildQuery(
            originLat, originLon,
            destLat, destLon,
            bicycleReluctance, bicycleBoardCost,
            bicycleSpeed,
            triangleTimeFactor, triangleSafetyFactor, triangleFlatnessFactor,
            hillReluctance,
            numItineraries,
            dateTime,
            arriveBy
        )

        return httpClient.post("$baseUrl/otp/routers/default/index/graphql") {
            contentType(ContentType.Application.Json)
            setBody(GraphQlRequest(query = query))
        }.body()
    }

    private fun buildQuery(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        bicycleReluctance: Double,
        bicycleBoardCost: Int,
        bicycleSpeed: Double,
        triangleTimeFactor: Double,
        triangleSafetyFactor: Double,
        triangleFlatnessFactor: Double,
        hillReluctance: Double,
        numItineraries: Int,
        dateTime: String?,
        arriveBy: Boolean
    ): String {
        val dateTimeClause = when {
            dateTime == null -> ""
            arriveBy -> """dateTime: { latestArrival: "$dateTime" }"""
            else -> """dateTime: { earliestDeparture: "$dateTime" }"""
        }

        return """
        {
          planConnection(
            origin: {
              location: { coordinate: { latitude: $originLat, longitude: $originLon } }
            }
            destination: {
              location: { coordinate: { latitude: $destLat, longitude: $destLon } }
            }
            $dateTimeClause
            first: $numItineraries
            modes: {
              direct: [BICYCLE]
              transit: {
                access: [BICYCLE]
                egress: [BICYCLE]
                transfer: [BICYCLE]
                transit: [
                  { mode: BUS }
                  { mode: RAIL }
                  { mode: TRAM }
                  { mode: FERRY }
                ]
              }
            }
            preferences: {
              street: {
                bicycle: {
                  reluctance: $bicycleReluctance
                  boardCost: $bicycleBoardCost
                  speed: $bicycleSpeed
                  hillReluctance: $hillReluctance
                  optimization: {
                    triangle: {
                      time: $triangleTimeFactor
                      safety: $triangleSafetyFactor
                      flatness: $triangleFlatnessFactor
                    }
                  }
                }
              }
            }
          ) {
            edges {
              node {
                duration
                startTime
                endTime
                numberOfTransfers
                legs {
                  mode
                  startTime
                  endTime
                  duration
                  distance
                  from {
                    name
                    lat
                    lon
                  }
                  to {
                    name
                    lat
                    lon
                  }
                  route {
                    shortName
                    longName
                  }
                  headsign
                  legGeometry { points }
                  steps {
                    distance
                    elevationProfile { distance elevation }
                  }
                }
              }
            }
          }
        }
    """.trimIndent()
    }
}

@Serializable
data class GraphQlRequest(
    val query: String
)
