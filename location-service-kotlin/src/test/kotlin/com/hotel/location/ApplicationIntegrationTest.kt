package com.hotel.location

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.hotel.location.dto.HealthResponse
import com.hotel.location.dto.LocationEventResponseDto
import com.hotel.location.dto.ProblemDetailsResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ApplicationIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val VALID_CORRELATION_ID = "a1b2c3d4-e5f6-4a8b-9c0d-1e2f3a4b5c6d"
    }

    @AfterEach
    fun tearDown() {
        resetServiceState()
    }

    private fun createLocationPayloadJson(
        hotelId: String? = "htl-recife-01",
        hotelLat: Double? = -8.052240,
        hotelLng: Double? = -34.885650,
        guestLat: Double? = -8.053100,
        guestLng: Double? = -34.886100,
        radius: Double? = 200.0,
        previousState: String? = "outside",
        omitField: String? = null
    ): String {
        val fields = mutableListOf<String>()
        if (omitField != "hotel_id") fields.add("\"hotel_id\": ${hotelId?.let { "\"$it\"" } ?: "null"}")
        if (omitField != "hotel_lat") fields.add("\"hotel_lat\": ${hotelLat ?: "null"}")
        if (omitField != "hotel_lng") fields.add("\"hotel_lng\": ${hotelLng ?: "null"}")
        if (omitField != "guest_lat") fields.add("\"guest_lat\": ${guestLat ?: "null"}")
        if (omitField != "guest_lng") fields.add("\"guest_lng\": ${guestLng ?: "null"}")
        if (omitField != "geofence_radius_m") fields.add("\"geofence_radius_m\": ${radius ?: "null"}")
        if (omitField != "previous_state") fields.add("\"previous_state\": ${previousState?.let { "\"$it\"" } ?: "null"}")
        return fields.joinToString(prefix = "{\n  ", postfix = "\n}", separator = ",\n  ")
    }

    @Nested
    @DisplayName("Success and Geofencing Scenarios")
    inner class GeofencingScenarios {

        @Test
        fun `should respond 200 OK on healthcheck`() = testApplication {
            application { module() }

            val response = client.get("/healthz")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `should respond 200 OK for valid location event`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<LocationEventResponseDto>(response.bodyAsText())
            assertEquals(VALID_CORRELATION_ID, body.correlation_id)
            assertEquals("htl-recife-01", body.hotel_id)
            assertEquals("inside", body.current_state)
            assertEquals("ENTERED", body.transition)
            assertTrue(body.alert_triggered)
            assertEquals("Hóspede entrou no raio de 200m da propriedade.", body.message)
        }

        @Test
        fun `should respond 200 OK with NO_CHANGE and no alert when guest remains inside radius (inside to inside)`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(previousState = "inside"))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<LocationEventResponseDto>(response.bodyAsText())
            assertEquals(VALID_CORRELATION_ID, body.correlation_id)
            assertEquals("htl-recife-01", body.hotel_id)
            assertEquals("inside", body.current_state)
            assertEquals("NO_CHANGE", body.transition)
            assertFalse(body.alert_triggered)
            assertEquals("Posição atualizada sem alerta.", body.message)
        }

        @Test
        fun `should respond 200 OK with EXITED and no alert when guest leaves radius (inside to outside)`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(
                    createLocationPayloadJson(
                        guestLat = -8.065000,
                        guestLng = -34.890000,
                        previousState = "inside"
                    )
                )
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<LocationEventResponseDto>(response.bodyAsText())
            assertEquals(VALID_CORRELATION_ID, body.correlation_id)
            assertEquals("htl-recife-01", body.hotel_id)
            assertEquals("outside", body.current_state)
            assertEquals("EXITED", body.transition)
            assertFalse(body.alert_triggered)
            assertEquals("Posição atualizada sem alerta.", body.message)
        }
    }

    @Nested
    @DisplayName("Input Validation and Error Scenarios")
    inner class InputValidationScenarios {

        @Test
        fun `should respond 400 Bad Request with RFC 7807 when X-Correlation-ID header is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:missing-header", error.type)
            assertEquals("Missing Required Header", error.title)
            assertEquals(400, error.status)
            assertTrue(error.detail.contains("X-Correlation-ID"))
            assertEquals("/v1/location-events", error.instance)
        }

        @Test
        fun `should respond 400 Bad Request when coordinates are invalid`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(guestLat = 95.0))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val responseBody = response.bodyAsText()
            assertTrue(
                responseBody.contains("INVALID_COORDINATES"),
                "O corpo da resposta de erro 400 deve conter literalmente 'INVALID_COORDINATES' conforme BDD da RFC-004"
            )
            val error = json.decodeFromString<ProblemDetailsResponse>(responseBody)
            assertEquals("urn:problem-type:invalid-coordinates", error.type)
            assertEquals("INVALID_COORDINATES", error.code)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when geofence radius is invalid`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(radius = -10.0))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-radius", error.type)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when previous_state is invalid`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(previousState = "invalido"))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-state", error.type)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when geofence_radius_m is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(omitField = "geofence_radius_m"))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when geofence_radius_m is null`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(radius = null))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when previous_state is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(omitField = "previous_state"))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when previous_state is null`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(previousState = null))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request for malformed json`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody("{ malformed json }")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:malformed-json", error.type)
            assertEquals("Malformed JSON Request", error.title)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 415 Unsupported Media Type when Content-Type header is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:unsupported-media-type", error.type)
            assertEquals("Unsupported Media Type", error.title)
            assertEquals(415, error.status)
        }

        @Test
        fun `should respond 415 Unsupported Media Type when Content-Type is not application-json`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, "text/plain")
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:unsupported-media-type", error.type)
            assertEquals("Unsupported Media Type", error.title)
            assertEquals(415, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when X-Correlation-ID header has invalid UUID`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", "uuid-invalido-12345")
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-header", error.type)
            assertEquals("Invalid Header", error.title)
            assertEquals(400, error.status)
            assertTrue(error.detail.contains("UUID"))
        }

        @Test
        fun `should respond 400 Bad Request when X-Correlation-ID is not a valid UUID v4`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", "a1b2c3d4-e5f6-1a8b-9c0d-1e2f3a4b5c6d")
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-header", error.type)
            assertEquals("Invalid Header", error.title)
            assertEquals(400, error.status)
            assertTrue(error.detail.contains("UUID"))
        }
    }

    @Nested
    @DisplayName("Resilience and Failure Simulation Scenarios")
    inner class ResilienceScenarios {

        @Test
        fun `should respond 503 Service Unavailable with RFC 7807 when service is unavailable via flag`() = testApplication {
            application { module() }

            isServiceAvailable = false

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:service-unavailable", error.type)
            assertEquals("Service Unavailable", error.title)
            assertEquals(503, error.status)
        }

        @Test
        fun `should respond 503 Service Unavailable with RFC 7807 when X-Mock-Service-Unavailable header is present`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                header("X-Mock-Service-Unavailable", "true")
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:service-unavailable", error.type)
            assertEquals("Service Unavailable", error.title)
            assertEquals(503, error.status)
        }

        @Test
        fun `should respond 503 Service Unavailable on healthcheck when service is unavailable`() = testApplication {
            application { module() }

            isServiceAvailable = false

            val response = client.get("/healthz")
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val body = json.decodeFromString<HealthResponse>(response.bodyAsText())
            assertEquals("DOWN", body.status)
            assertEquals("location-service-kotlin", body.service)
            assertEquals(8104, body.port)
        }

        @Test
        fun `should respond with application problem json content type on error`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header("Content-Type", "application/json")
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(guestLat = 95.0))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val contentType = response.contentType()
            assertNotNull(contentType)
            assertEquals("application", contentType?.contentType)
            assertEquals("problem+json", contentType?.contentSubtype)
        }
    }

    @Nested
    @DisplayName("Observability and Structured Logging Scenarios")
    inner class ObservabilityScenarios {

        @Test
        fun `should log structured JSON for GEOFENCE_EVALUATED event`() = testApplication {
            application { module() }

            val logbackLogger = LoggerFactory.getLogger("com.hotel.location.Application") as Logger
            val listAppender = ListAppender<ILoggingEvent>()
            listAppender.start()
            logbackLogger.addAppender(listAppender)

            try {
                val response = client.post("/v1/location-events") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(createLocationPayloadJson())
                }

                assertEquals(HttpStatusCode.OK, response.status)

                val logEvent = listAppender.list.firstOrNull { it.formattedMessage.contains("GEOFENCE_EVALUATED") }
                assertNotNull(logEvent, "Deveria ter registrado log com evento GEOFENCE_EVALUATED")

                val jsonLog = json.parseToJsonElement(logEvent!!.formattedMessage).jsonObject
                assertEquals("INFO", jsonLog["level"]?.jsonPrimitive?.content)
                assertEquals(VALID_CORRELATION_ID, jsonLog["correlation_id"]?.jsonPrimitive?.content)
                assertEquals("GEOFENCE_EVALUATED", jsonLog["event"]?.jsonPrimitive?.content)
                assertEquals("htl-recife-01", jsonLog["hotel_id"]?.jsonPrimitive?.content)
                assertEquals(107.7, jsonLog["distance_meters"]?.jsonPrimitive?.double)
                assertEquals("ENTERED", jsonLog["transition"]?.jsonPrimitive?.content)
                assertNotNull(jsonLog["duration_ms"]?.jsonPrimitive?.double)
                assertNotNull(jsonLog["timestamp"]?.jsonPrimitive?.content)

                assertFalse(jsonLog.containsKey("hotel_lat"))
                assertFalse(jsonLog.containsKey("hotel_lng"))
                assertFalse(jsonLog.containsKey("guest_lat"))
                assertFalse(jsonLog.containsKey("guest_lng"))
            } finally {
                logbackLogger.detachAppender(listAppender)
            }
        }

        @Test
        fun `should log structured JSON with GEOFENCE_VALIDATION_FAILED event on coordinate validation error`() = testApplication {
            application { module() }

            val logbackLogger = LoggerFactory.getLogger("com.hotel.location.Application") as Logger
            val listAppender = ListAppender<ILoggingEvent>()
            listAppender.start()
            logbackLogger.addAppender(listAppender)

            try {
                val response = client.post("/v1/location-events") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(createLocationPayloadJson(guestLat = 999.0))
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)

                val logEvent = listAppender.list.firstOrNull { it.formattedMessage.contains("GEOFENCE_VALIDATION_FAILED") }
                assertNotNull(logEvent, "Deveria ter registrado log estruturado com evento GEOFENCE_VALIDATION_FAILED")

                val jsonLog = json.parseToJsonElement(logEvent!!.formattedMessage).jsonObject
                assertEquals("WARN", jsonLog["level"]?.jsonPrimitive?.content)
                assertEquals(VALID_CORRELATION_ID, jsonLog["correlation_id"]?.jsonPrimitive?.content)
                assertEquals("GEOFENCE_VALIDATION_FAILED", jsonLog["event"]?.jsonPrimitive?.content)
                assertEquals("urn:problem-type:invalid-coordinates", jsonLog["error_type"]?.jsonPrimitive?.content)
                assertEquals("INVALID_COORDINATES", jsonLog["code"]?.jsonPrimitive?.content)
                assertEquals(400, jsonLog["status_code"]?.jsonPrimitive?.int)
                assertEquals("/v1/location-events", jsonLog["path"]?.jsonPrimitive?.content)
                assertNotNull(jsonLog["timestamp"]?.jsonPrimitive?.content)
                assertNotNull(jsonLog["message"]?.jsonPrimitive?.content)

                assertFalse(jsonLog.containsKey("hotel_lat"))
                assertFalse(jsonLog.containsKey("hotel_lng"))
                assertFalse(jsonLog.containsKey("guest_lat"))
                assertFalse(jsonLog.containsKey("guest_lng"))
            } finally {
                logbackLogger.detachAppender(listAppender)
            }
        }

        @Test
        fun `should log structured JSON with GEOFENCE_SERVICE_UNAVAILABLE event when service is simulated unavailable`() = testApplication {
            application { module() }

            val logbackLogger = LoggerFactory.getLogger("com.hotel.location.Application") as Logger
            val listAppender = ListAppender<ILoggingEvent>()
            listAppender.start()
            logbackLogger.addAppender(listAppender)

            try {
                val response = client.post("/v1/location-events") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    header("X-Mock-Service-Unavailable", "true")
                    setBody(createLocationPayloadJson())
                }

                assertEquals(HttpStatusCode.ServiceUnavailable, response.status)

                val logEvent = listAppender.list.firstOrNull { it.formattedMessage.contains("GEOFENCE_SERVICE_UNAVAILABLE") }
                assertNotNull(logEvent, "Deveria ter registrado log estruturado com evento GEOFENCE_SERVICE_UNAVAILABLE")

                val jsonLog = json.parseToJsonElement(logEvent!!.formattedMessage).jsonObject
                assertEquals("ERROR", jsonLog["level"]?.jsonPrimitive?.content)
                assertEquals(VALID_CORRELATION_ID, jsonLog["correlation_id"]?.jsonPrimitive?.content)
                assertEquals("GEOFENCE_SERVICE_UNAVAILABLE", jsonLog["event"]?.jsonPrimitive?.content)
                assertEquals("urn:problem-type:service-unavailable", jsonLog["error_type"]?.jsonPrimitive?.content)
                assertEquals("SERVICE_UNAVAILABLE", jsonLog["code"]?.jsonPrimitive?.content)
                assertEquals(503, jsonLog["status_code"]?.jsonPrimitive?.int)
                assertEquals("/v1/location-events", jsonLog["path"]?.jsonPrimitive?.content)
                assertNotNull(jsonLog["timestamp"]?.jsonPrimitive?.content)
            } finally {
                logbackLogger.detachAppender(listAppender)
            }
        }

        @Test
        fun `should log structured JSON with MALFORMED_JSON_ERROR event when payload is malformed`() = testApplication {
            application { module() }

            val logbackLogger = LoggerFactory.getLogger("com.hotel.location.Application") as Logger
            val listAppender = ListAppender<ILoggingEvent>()
            listAppender.start()
            logbackLogger.addAppender(listAppender)

            try {
                val response = client.post("/v1/location-events") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody("{ malformed json }")
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)

                val logEvent = listAppender.list.firstOrNull { it.formattedMessage.contains("MALFORMED_JSON_ERROR") }
                assertNotNull(logEvent, "Deveria ter registrado log estruturado com evento MALFORMED_JSON_ERROR")

                val jsonLog = json.parseToJsonElement(logEvent!!.formattedMessage).jsonObject
                assertEquals("WARN", jsonLog["level"]?.jsonPrimitive?.content)
                assertEquals(VALID_CORRELATION_ID, jsonLog["correlation_id"]?.jsonPrimitive?.content)
                assertEquals("MALFORMED_JSON_ERROR", jsonLog["event"]?.jsonPrimitive?.content)
                assertEquals("urn:problem-type:malformed-json", jsonLog["error_type"]?.jsonPrimitive?.content)
                assertEquals("MALFORMED_JSON", jsonLog["code"]?.jsonPrimitive?.content)
                assertEquals(400, jsonLog["status_code"]?.jsonPrimitive?.int)
            } finally {
                logbackLogger.detachAppender(listAppender)
            }
        }
    }
}
