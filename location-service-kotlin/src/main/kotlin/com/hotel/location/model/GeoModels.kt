package com.hotel.location.model

import com.hotel.location.exception.InvalidCoordinatesException
import com.hotel.location.exception.InvalidGeofenceRadiusException
import com.hotel.location.exception.MissingFieldException

data class Coordinates(
    val latitude: Double,
    val longitude: Double
) {
    init {
        validateLatitude(latitude)
        validateLongitude(longitude)
    }

    companion object {
        private val VALID_LATITUDE_RANGE = -90.0..90.0
        private val VALID_LONGITUDE_RANGE = -180.0..180.0

        private fun validateLatitude(lat: Double) {
            if (!lat.isFinite() || lat !in VALID_LATITUDE_RANGE) {
                throw InvalidCoordinatesException(
                    "Latitude deve ser finita e estar entre -90.0 e 90.0 (recebido: $lat).",
                    field = "latitude"
                )
            }
        }

        private fun validateLongitude(lng: Double) {
            if (!lng.isFinite() || lng !in VALID_LONGITUDE_RANGE) {
                throw InvalidCoordinatesException(
                    "Longitude deve ser finita e estar entre -180.0 e 180.0 (recebido: $lng).",
                    field = "longitude"
                )
            }
        }
    }
}

data class LocationEvent(
    val hotelId: String,
    val hotelLocation: Coordinates,
    val guestLocation: Coordinates,
    val geofenceRadiusMeters: Double,
    val previousState: GeofenceState
) {
    init {
        if (hotelId.isNullOrBlank()) {
            throw MissingFieldException("hotel_id")
        }
        if (!geofenceRadiusMeters.isFinite() || geofenceRadiusMeters <= 0.0) {
            throw InvalidGeofenceRadiusException(geofenceRadiusMeters)
        }
    }
}

data class GeofenceEvaluationResult(
    val correlationId: String,
    val hotelId: String,
    val distanceMeters: Double,
    val currentState: GeofenceState,
    val transition: GeofenceTransition,
    val alertTriggered: Boolean,
    val message: String
)
