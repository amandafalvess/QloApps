package com.hotel.location

import com.hotel.location.exception.InvalidCoordinatesException
import com.hotel.location.exception.InvalidGeofenceRadiusException
import com.hotel.location.exception.MissingFieldException
import com.hotel.location.dto.toLog
import com.hotel.location.model.Coordinates
import com.hotel.location.model.GeofenceState
import com.hotel.location.model.GeofenceTransition
import com.hotel.location.model.LocationEvent
import com.hotel.location.service.HaversineEngine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HaversineEngineTest {

    @Nested
    @DisplayName("Haversine Distance Calculation")
    inner class DistanceCalculationTests {

        @Test
        fun `should calculate approximate distance of 108 meters for nearby guest`() {
            val hotelLat = -8.052240
            val hotelLng = -34.885650
            val guestLat = -8.053100
            val guestLng = -34.886100

            val distance = HaversineEngine.calculateDistanceMeters(hotelLat, hotelLng, guestLat, guestLng)

            assertEquals(107.7, distance, 1.5, "A distância calculada deve ser aproximadamente 108 metros.")
        }

        @Test
        fun `should calculate approximate distance of 1500 meters for distant guest`() {
            val hotelLat = -8.052240
            val hotelLng = -34.885650
            val guestLat = -8.065000
            val guestLng = -34.890000

            val distance = HaversineEngine.calculateDistanceMeters(hotelLat, hotelLng, guestLat, guestLng)

            assertEquals(1497.5, distance, 5.0, "A distância calculada deve ser aproximadamente 1500 metros.")
        }

        @Test
        fun `should return zero when coordinates are identical`() {
            val lat = -8.052240
            val lng = -34.885650

            val distance = HaversineEngine.calculateDistanceMeters(lat, lng, lat, lng)

            assertEquals(0.0, distance, 0.001, "A distância entre pontos idênticos deve ser 0.0 metros.")
        }
    }

    @Nested
    @DisplayName("Coordinates and Entities Validation")
    inner class CoordinateValidationTests {

        @Test
        fun `should validate coordinate boundaries correctly`() {
            assertTrue(HaversineEngine.isValidLatitude(0.0))
            assertTrue(HaversineEngine.isValidLatitude(90.0))
            assertTrue(HaversineEngine.isValidLatitude(-90.0))
            assertFalse(HaversineEngine.isValidLatitude(90.1))
            assertFalse(HaversineEngine.isValidLatitude(-90.1))

            assertTrue(HaversineEngine.isValidLongitude(0.0))
            assertTrue(HaversineEngine.isValidLongitude(180.0))
            assertTrue(HaversineEngine.isValidLongitude(-180.0))
            assertFalse(HaversineEngine.isValidLongitude(180.1))
            assertFalse(HaversineEngine.isValidLongitude(-180.1))
        }

        @Test
        fun `should throw exception when instantiating coordinates out of bounds`() {
            assertThrows(InvalidCoordinatesException::class.java) {
                Coordinates(95.0, 0.0)
            }
            assertThrows(InvalidCoordinatesException::class.java) {
                Coordinates(-90.1, 0.0)
            }
            assertThrows(InvalidCoordinatesException::class.java) {
                Coordinates(0.0, 185.0)
            }
            assertThrows(InvalidCoordinatesException::class.java) {
                Coordinates(Double.NaN, 0.0)
            }
        }

        @Test
        fun `should throw exception when geofence radius is less than or equal to zero`() {
            assertThrows(InvalidGeofenceRadiusException::class.java) {
                LocationEvent(
                    hotelId = "htl-01",
                    hotelLocation = Coordinates(-8.052240, -34.885650),
                    guestLocation = Coordinates(-8.053100, -34.886100),
                    geofenceRadiusMeters = 0.0,
                    previousState = GeofenceState.OUTSIDE
                )
            }
            assertThrows(InvalidGeofenceRadiusException::class.java) {
                LocationEvent(
                    hotelId = "htl-01",
                    hotelLocation = Coordinates(-8.052240, -34.885650),
                    guestLocation = Coordinates(-8.053100, -34.886100),
                    geofenceRadiusMeters = -50.0,
                    previousState = GeofenceState.OUTSIDE
                )
            }
        }

        @Test
        fun `should throw MissingFieldException when hotelId is empty or blank in domain model`() {
            val exEmpty = assertThrows(MissingFieldException::class.java) {
                LocationEvent(
                    hotelId = "",
                    hotelLocation = Coordinates(-8.052240, -34.885650),
                    guestLocation = Coordinates(-8.053100, -34.886100),
                    geofenceRadiusMeters = 200.0,
                    previousState = GeofenceState.OUTSIDE
                )
            }
            assertEquals("hotel_id", exEmpty.field)
            assertEquals("O campo 'hotel_id' é obrigatório.", exEmpty.message)

            val exBlank = assertThrows(MissingFieldException::class.java) {
                LocationEvent(
                    hotelId = "   ",
                    hotelLocation = Coordinates(-8.052240, -34.885650),
                    guestLocation = Coordinates(-8.053100, -34.886100),
                    geofenceRadiusMeters = 200.0,
                    previousState = GeofenceState.OUTSIDE
                )
            }
            assertEquals("hotel_id", exBlank.field)
        }

        @Test
        fun `should report correct field when latitude or longitude validation fails`() {
            val exLat = assertThrows(InvalidCoordinatesException::class.java) {
                Coordinates(95.0, 0.0)
            }
            assertEquals("latitude", exLat.field)

            val exLng = assertThrows(InvalidCoordinatesException::class.java) {
                Coordinates(0.0, 185.0)
            }
            assertEquals("longitude", exLng.field)
        }
    }

    @Nested
    @DisplayName("Geofence Evaluation and State Transitions")
    inner class GeofenceEvaluationTests {

        @Test
        fun `should trigger alert with ENTERED transition when entering radius`() {
            val event = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = Coordinates(-8.052240, -34.885650),
                guestLocation = Coordinates(-8.053100, -34.886100),
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.OUTSIDE
            )

            val result = HaversineEngine.evaluate(event, "test-corr-01")

            assertEquals(GeofenceState.INSIDE, result.currentState)
            assertEquals(GeofenceTransition.ENTERED, result.transition)
            assertTrue(result.alertTriggered)
            assertEquals("Hóspede entrou no raio de 200m da propriedade.", result.message)
        }

        @Test
        fun `should return NO_CHANGE and no alert when guest is distant`() {
            val event = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = Coordinates(-8.052240, -34.885650),
                guestLocation = Coordinates(-8.065000, -34.890000),
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.OUTSIDE
            )

            val result = HaversineEngine.evaluate(event, "test-corr-02")

            assertEquals(GeofenceState.OUTSIDE, result.currentState)
            assertEquals(GeofenceTransition.NO_CHANGE, result.transition)
            assertFalse(result.alertTriggered)
        }

        @Test
        fun `should record EXITED transition when leaving radius`() {
            val event = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = Coordinates(-8.052240, -34.885650),
                guestLocation = Coordinates(-8.065000, -34.890000),
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.INSIDE
            )

            val result = HaversineEngine.evaluate(event, "test-corr-03")

            assertEquals(GeofenceState.OUTSIDE, result.currentState)
            assertEquals(GeofenceTransition.EXITED, result.transition)
            assertFalse(result.alertTriggered)
        }

        @Test
        fun `should return NO_CHANGE and no alert when guest remains inside radius (inside to inside)`() {
            val event = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = Coordinates(-8.052240, -34.885650),
                guestLocation = Coordinates(-8.053100, -34.886100),
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.INSIDE
            )

            val result = HaversineEngine.evaluate(event, "test-corr-inside-inside")

            assertEquals(GeofenceState.INSIDE, result.currentState)
            assertEquals(GeofenceTransition.NO_CHANGE, result.transition)
            assertFalse(result.alertTriggered)
            assertEquals("Posição atualizada sem alerta.", result.message)
        }

        @Test
        fun `should return NO_CHANGE and no alert when coordinates are identical and previous_state is inside`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val event = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = hotelCoords,
                guestLocation = hotelCoords,
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.INSIDE
            )

            val result = HaversineEngine.evaluate(event, "test-corr-exact-inside")

            assertEquals(0.0, result.distanceMeters)
            assertEquals(GeofenceState.INSIDE, result.currentState)
            assertEquals(GeofenceTransition.NO_CHANGE, result.transition)
            assertFalse(result.alertTriggered)
        }

        @Test
        fun `should trigger ENTERED alert when coordinates are identical and previous_state is outside`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val event = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = hotelCoords,
                guestLocation = hotelCoords,
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.OUTSIDE
            )

            val result = HaversineEngine.evaluate(event, "test-corr-exact-outside")

            assertEquals(0.0, result.distanceMeters)
            assertEquals(GeofenceState.INSIDE, result.currentState)
            assertEquals(GeofenceTransition.ENTERED, result.transition)
            assertTrue(result.alertTriggered)
            assertEquals("Hóspede entrou no raio de 200m da propriedade.", result.message)
        }

        @Test
        fun `should dynamically parameterize radius in message when triggering ENTERED alert`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val guestCoords = Coordinates(-8.053100, -34.886100)

            val event500 = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = 500.0,
                previousState = GeofenceState.OUTSIDE
            )
            val result500 = HaversineEngine.evaluate(event500, "test-corr-500m")
            assertTrue(result500.alertTriggered)
            assertEquals("Hóspede entrou no raio de 500m da propriedade.", result500.message)

            val eventDecimal = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = 150.5,
                previousState = GeofenceState.OUTSIDE
            )
            val resultDecimal = HaversineEngine.evaluate(eventDecimal, "test-corr-150-5m")
            assertTrue(resultDecimal.alertTriggered)
            assertEquals("Hóspede entrou no raio de 150.5m da propriedade.", resultDecimal.message)
        }

        @Test
        fun `should consider inside when distance is exactly equal to configured radius`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val guestCoords = Coordinates(-8.053100, -34.886100)
            val calculatedDistance = HaversineEngine.calculateDistanceMeters(hotelCoords, guestCoords)

            val eventInside = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = calculatedDistance,
                previousState = GeofenceState.INSIDE
            )
            val resultInside = HaversineEngine.evaluate(eventInside, "test-corr-exact-radius-inside")
            assertEquals(GeofenceState.INSIDE, resultInside.currentState)
            assertEquals(GeofenceTransition.NO_CHANGE, resultInside.transition)
            assertFalse(resultInside.alertTriggered)

            val eventOutside = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = calculatedDistance,
                previousState = GeofenceState.OUTSIDE
            )
            val resultOutside = HaversineEngine.evaluate(eventOutside, "test-corr-exact-radius-outside")
            assertEquals(GeofenceState.INSIDE, resultOutside.currentState)
            assertEquals(GeofenceTransition.ENTERED, resultOutside.transition)
            assertTrue(resultOutside.alertTriggered)
        }
    }

    @Nested
    @DisplayName("Observability and Performance")
    inner class ObservabilityAndPerformanceTests {

        @Test
        fun `should generate structured log with schema without GPS coordinates`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val guestCoords = Coordinates(-8.053100, -34.886100)
            val event = LocationEvent(
                hotelId = "htl-recife-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.OUTSIDE
            )

            val result = HaversineEngine.evaluate(event, "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
            val logObj = result.toLog(durationMs = 1.12, timestamp = "2026-08-27T10:30:12.441Z")

            val jsonString = Json.encodeToString(logObj)
            val jsonElement = Json.parseToJsonElement(jsonString).jsonObject

            assertEquals("2026-08-27T10:30:12.441Z", jsonElement["timestamp"]?.jsonPrimitive?.content)
            assertEquals("INFO", jsonElement["level"]?.jsonPrimitive?.content)
            assertEquals("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d", jsonElement["correlation_id"]?.jsonPrimitive?.content)
            assertEquals("GEOFENCE_EVALUATED", jsonElement["event"]?.jsonPrimitive?.content)
            assertEquals("htl-recife-01", jsonElement["hotel_id"]?.jsonPrimitive?.content)
            assertEquals(107.7, jsonElement["distance_meters"]?.jsonPrimitive?.double)
            assertEquals("ENTERED", jsonElement["transition"]?.jsonPrimitive?.content)
            assertEquals(1.12, jsonElement["duration_ms"]?.jsonPrimitive?.double)

            assertFalse(jsonElement.containsKey("hotel_lat"))
            assertFalse(jsonElement.containsKey("hotel_lng"))
            assertFalse(jsonElement.containsKey("guest_lat"))
            assertFalse(jsonElement.containsKey("guest_lng"))
        }

        @Test
        fun `should comply with P95 latency SLA under 10ms for haversine calculation and geofence evaluation`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val guestCoords = Coordinates(-8.053100, -34.886100)
            val event = LocationEvent(
                hotelId = "htl-recife-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.OUTSIDE
            )

            repeat(100) {
                HaversineEngine.evaluate(event, "warmup-corr-id")
            }

            val iterations = 500
            val durationsMs = mutableListOf<Double>()
            repeat(iterations) {
                val start = System.nanoTime()
                HaversineEngine.evaluate(event, "sla-corr-id")
                val duration = (System.nanoTime() - start) / 1_000_000.0
                durationsMs.add(duration)
            }

            durationsMs.sort()
            val p95Index = (iterations * 0.95).toInt()
            val p95LatencyMs = durationsMs[p95Index]

            assertTrue(
                p95LatencyMs < 10.0,
                "A latência P95 deve ser estritamente inferior a 10ms conforme SLA da RFC-004 (medido: ${p95LatencyMs}ms)"
            )
        }
    }
}
