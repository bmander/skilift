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
        numItineraries: Int = 5
    ): OtpPlanConnectionResponse {
        val query = buildQuery(
            originLat, originLon,
            destLat, destLon,
            bicycleReluctance, bicycleBoardCost,
            bicycleSpeed, numItineraries
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
        numItineraries: Int
    ): String = """
        {
          planConnection(
            origin: {
              coordinate: { latitude: $originLat, longitude: $originLon }
            }
            destination: {
              coordinate: { latitude: $destLat, longitude: $destLon }
            }
            first: $numItineraries
            modes: {
              directMode: BICYCLE
              transitModes: [
                { mode: BUS }
                { mode: RAIL }
                { mode: TRAM }
                { mode: FERRY }
              ]
              accessMode: BICYCLE
              egressMode: BICYCLE
              transferMode: WALK
            }
            preferences: {
              street: {
                bicycle: {
                  reluctance: $bicycleReluctance
                  boardCost: $bicycleBoardCost
                  speed: $bicycleSpeed
                  optimization: SAFE_STREETS
                }
              }
            }
          ) {
            edges {
              node {
                duration
                startTime
                endTime
                walkDistance
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
                    stop { code platformCode }
                  }
                  to {
                    name
                    lat
                    lon
                    stop { code platformCode }
                  }
                  route {
                    shortName
                    longName
                  }
                  headsign
                  legGeometry { points }
                }
              }
            }
          }
        }
    """.trimIndent()
}

@Serializable
data class GraphQlRequest(
    val query: String
)
