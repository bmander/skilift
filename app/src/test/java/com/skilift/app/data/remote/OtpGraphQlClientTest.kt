package com.skilift.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpGraphQlClientTest {

    private val testJson = Json { ignoreUnknownKeys = true }

    private fun createClientWithMockResponse(responseBody: String): Pair<OtpGraphQlClient, MockEngine> {
        val mockEngine = MockEngine { _ ->
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }
        return OtpGraphQlClient(httpClient, "http://localhost:8080") to mockEngine
    }

    @Test
    fun `planConnection sends request to correct URL`() = runTest {
        var requestUrl = ""
        val mockEngine = MockEngine { request ->
            requestUrl = request.url.toString()
            respond(
                content = EMPTY_SUCCESS_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }
        val client = OtpGraphQlClient(httpClient, "http://localhost:8080")

        client.planConnection(47.6, -122.3, 47.65, -122.35)

        assertEquals(
            "http://localhost:8080/otp/routers/default/index/graphql",
            requestUrl
        )
    }

    @Test
    fun `planConnection sends GraphQL query in request body`() = runTest {
        var requestBody = ""
        val mockEngine = MockEngine { request ->
            requestBody = request.body.toString()
            respond(
                content = EMPTY_SUCCESS_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }
        val client = OtpGraphQlClient(httpClient, "http://localhost:8080")

        client.planConnection(47.6, -122.3, 47.65, -122.35)

        // The body should contain a JSON-serialized GraphQlRequest with the query
        assertTrue("Request body should not be empty", requestBody.isNotEmpty())
    }

    @Test
    fun `planConnection parses successful response`() = runTest {
        val (client, _) = createClientWithMockResponse(FULL_SUCCESS_RESPONSE)

        val response = client.planConnection(47.6, -122.3, 47.65, -122.35)

        assertNotNull(response.data)
        val edges = response.data!!.planConnection.edges
        assertEquals(1, edges.size)
        assertEquals(1800, edges[0].node.duration)
        assertEquals(2, edges[0].node.legs.size)
        assertEquals("BICYCLE", edges[0].node.legs[0].mode)
        assertEquals("BUS", edges[0].node.legs[1].mode)
    }

    @Test
    fun `planConnection parses error response`() = runTest {
        val (client, _) = createClientWithMockResponse(ERROR_RESPONSE)

        val response = client.planConnection(47.6, -122.3, 47.65, -122.35)

        assertNotNull(response.errors)
        assertEquals(1, response.errors!!.size)
        assertEquals("No trip found", response.errors!![0].message)
    }

    @Test
    fun `planConnection includes dateTime for departure`() = runTest {
        var capturedBody = ""
        val mockEngine = MockEngine { request ->
            // Read the request body as text via channel
            capturedBody = (request.body as TextContent).text
            respond(
                content = EMPTY_SUCCESS_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }
        val client = OtpGraphQlClient(httpClient, "http://localhost:8080")

        client.planConnection(
            47.6, -122.3, 47.65, -122.35,
            dateTime = "2024-01-15T08:00:00-08:00",
            arriveBy = false
        )

        assertTrue(
            "Should contain earliestDeparture",
            capturedBody.contains("earliestDeparture")
        )
    }

    @Test
    fun `planConnection includes dateTime for arrival`() = runTest {
        var capturedBody = ""
        val mockEngine = MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
                content = EMPTY_SUCCESS_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }
        val client = OtpGraphQlClient(httpClient, "http://localhost:8080")

        client.planConnection(
            47.6, -122.3, 47.65, -122.35,
            dateTime = "2024-01-15T08:00:00-08:00",
            arriveBy = true
        )

        assertTrue(
            "Should contain latestArrival",
            capturedBody.contains("latestArrival")
        )
    }

    @Test
    fun `planConnection passes bicycle parameters`() = runTest {
        var capturedBody = ""
        val mockEngine = MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
                content = EMPTY_SUCCESS_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }
        val client = OtpGraphQlClient(httpClient, "http://localhost:8080")

        client.planConnection(
            47.6, -122.3, 47.65, -122.35,
            bicycleReluctance = 3.5,
            bicycleBoardCost = 800,
            bicycleSpeed = 6.0,
            triangleTimeFactor = 0.5,
            triangleSafetyFactor = 0.3,
            triangleFlatnessFactor = 0.2,
            hillReluctance = 2.0
        )

        assertTrue("Should contain reluctance", capturedBody.contains("3.5"))
        assertTrue("Should contain boardCost", capturedBody.contains("800"))
        assertTrue("Should contain speed", capturedBody.contains("6.0"))
        assertTrue("Should contain hillReluctance", capturedBody.contains("2.0"))
    }

    companion object {
        private val EMPTY_SUCCESS_RESPONSE = """
            {
                "data": {
                    "planConnection": {
                        "edges": []
                    }
                }
            }
        """.trimIndent()

        private val FULL_SUCCESS_RESPONSE = """
            {
                "data": {
                    "planConnection": {
                        "edges": [
                            {
                                "node": {
                                    "duration": 1800,
                                    "startTime": 1700000000000,
                                    "endTime": 1700001800000,
                                    "numberOfTransfers": 1,
                                    "legs": [
                                        {
                                            "mode": "BICYCLE",
                                            "startTime": 1700000000000,
                                            "endTime": 1700000600000,
                                            "duration": 600.0,
                                            "distance": 2000.0,
                                            "from": { "name": "Origin", "lat": 47.6, "lon": -122.3 },
                                            "to": { "name": "Stop A", "lat": 47.61, "lon": -122.31 }
                                        },
                                        {
                                            "mode": "BUS",
                                            "startTime": 1700000600000,
                                            "endTime": 1700001800000,
                                            "duration": 1200.0,
                                            "distance": 5000.0,
                                            "from": { "name": "Stop A", "lat": 47.61, "lon": -122.31 },
                                            "to": { "name": "Destination", "lat": 47.65, "lon": -122.35 },
                                            "route": { "shortName": "40", "longName": "Ballard" },
                                            "headsign": "Downtown Seattle"
                                        }
                                    ]
                                }
                            }
                        ]
                    }
                }
            }
        """.trimIndent()

        private val ERROR_RESPONSE = """
            {
                "errors": [
                    { "message": "No trip found" }
                ]
            }
        """.trimIndent()
    }
}
