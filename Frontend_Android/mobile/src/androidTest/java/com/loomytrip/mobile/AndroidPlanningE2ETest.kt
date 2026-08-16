package com.loomytrip.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loomytrip.mobile.data.network.PlanningApi
import com.loomytrip.mobile.data.repository.AiPlanningRepository
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@RunWith(AndroidJUnit4::class)
class AndroidPlanningE2ETest {

    private lateinit var server: MockWebServer
    private lateinit var api: PlanningApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PlanningApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun asyncImport_reviewChanges_validateAndConfirm_matchWebContract() = runBlocking {
        enqueueJson("""{"id":71,"status":"PROCESSING"}""", 201)
        enqueueJson("""{"id":71,"status":"PROCESSING"}""")
        enqueueJson(sessionJson(validationStatus = "UNVALIDATED", secondPlace = true))
        enqueueJson(sessionJson(validationStatus = "VALID", secondPlace = true))

        val imported = AiPlanningRepository.startSession(
            sourceText = "Plan a two day Singapore trip",
            api = api,
            pollAttempts = 3,
            pollIntervalMs = 0
        )

        assertEquals(71L, imported.sessionId)
        assertEquals(2, imported.durationDays)
        assertEquals(2, imported.places.size)
        assertEquals("Located", imported.places.first().suggestedTime)

        assertRequest("POST", "/api/v1/planning-sessions", "two day Singapore trip")
        assertRequest("GET", "/api/v1/planning-sessions/71")
        assertRequest("GET", "/api/v1/planning-sessions/71")
        assertRequest("POST", "/api/v1/planning-sessions/71/validate-places")

        server.enqueue(MockResponse().setResponseCode(204))
        AiPlanningRepository.renamePlace(11, "Gardens by the Bay", api)
        assertRequest("PUT", "/api/v1/planning-sessions/draft-places/11", "Gardens by the Bay")

        server.enqueue(MockResponse().setResponseCode(204))
        AiPlanningRepository.assignDay(11, 2, api)
        assertRequest("PUT", "/api/v1/planning-sessions/draft-places/11", "\"suggestedDay\":2")

        server.enqueue(MockResponse().setResponseCode(204))
        AiPlanningRepository.deletePlace(12, api)
        assertRequest("DELETE", "/api/v1/planning-sessions/draft-places/12")

        server.enqueue(MockResponse().setResponseCode(204))
        enqueueJson(sessionJson(validationStatus = "UNVALIDATED", secondPlace = false))
        enqueueJson(sessionJson(validationStatus = "VALID", secondPlace = false))
        val refined = AiPlanningRepository.refine(71, "Keep the outdoor stop in the morning", api)
        assertEquals(1, refined.places.size)
        assertRequest("POST", "/api/v1/planning-sessions/71/messages", "outdoor stop")
        assertRequest("POST", "/api/v1/planning-sessions/71/refine")
        assertRequest("POST", "/api/v1/planning-sessions/71/validate-places")

        enqueueJson(sessionJson(validationStatus = "VALID", secondPlace = false))
        val validated = AiPlanningRepository.validatePlaces(71, api)
        assertTrue(validated.places.all { it.suggestedTime == "Located" })
        assertRequest("POST", "/api/v1/planning-sessions/71/validate-places")

        enqueueJson(
            """
            {
              "id":501,
              "tripName":"Singapore Highlights",
              "durationDays":2,
              "weatherSummary":"Light rain is expected in the afternoon.",
              "suggestedAdditions":[{
                "name":"Marina Barrage",
                "type":"ATTRACTION",
                "latitude":1.2807,
                "longitude":103.8707,
                "distanceKm":1.4,
                "reason":"Close to the confirmed route",
                "activities":["Sunset walk"]
              }]
            }
            """.trimIndent()
        )
        val confirmation = AiPlanningRepository.confirm(71, 2, api)

        assertEquals(501L, confirmation.tripId)
        assertEquals("Light rain is expected in the afternoon.", confirmation.weatherSummary)
        assertEquals("Marina Barrage", confirmation.suggestedAdditions.single().name)
        assertRequest("POST", "/api/v1/planning-sessions/71/confirm", "\"durationDays\":2")
    }

    private fun enqueueJson(body: String, status: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    private fun assertRequest(method: String, path: String, bodyContains: String? = null) {
        val request = server.takeRequest()
        assertEquals(method, request.method)
        assertEquals(path, request.path)
        bodyContains?.let { expected -> assertTrue(request.body.readUtf8().contains(expected)) }
    }

    private fun sessionJson(validationStatus: String, secondPlace: Boolean): String {
        val places = buildString {
            append(
                """{"id":11,"name":"Gardens by the Bay","validationStatus":"$validationStatus","suggestedDay":2}"""
            )
            if (secondPlace) {
                append(
                    """,{"id":12,"name":"National Gallery Singapore","validationStatus":"$validationStatus","suggestedDay":1}"""
                )
            }
        }
        return """{"id":71,"status":"DRAFT_READY","durationDays":2,"failureCode":null,"failureReason":null,"draftPlaces":[$places]}"""
    }
}
