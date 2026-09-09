package com.hotel.location.service

import com.hotel.location.model.Coordinates
import com.hotel.location.model.GeofenceEvaluationResult
import com.hotel.location.model.GeofenceState
import com.hotel.location.model.GeofenceTransition
import com.hotel.location.model.LocationEvent
import kotlin.math.*

object HaversineEngine {
    private const val EARTH_RADIUS_METERS = 6371000.0

    fun isValidLatitude(lat: Double): Boolean = lat.isFinite() && lat in -90.0..90.0

    fun isValidLongitude(lng: Double): Boolean = lng.isFinite() && lng in -180.0..180.0

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val clampedA = a.coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
        return EARTH_RADIUS_METERS * c
    }

    fun calculateDistanceMeters(c1: Coordinates, c2: Coordinates): Double {
        return calculateDistanceMeters(c1.latitude, c1.longitude, c2.latitude, c2.longitude)
    }

    fun evaluate(event: LocationEvent, correlationId: String): GeofenceEvaluationResult {
        val distance = calculateDistanceMeters(event.hotelLocation, event.guestLocation)
        val roundedDistance = (distance * 10.0).roundToInt() / 10.0

        val currentState = if (distance <= event.geofenceRadiusMeters) {
            GeofenceState.INSIDE
        } else {
            GeofenceState.OUTSIDE
        }

        val transition = when {
            event.previousState == GeofenceState.OUTSIDE && currentState == GeofenceState.INSIDE -> GeofenceTransition.ENTERED
            event.previousState == GeofenceState.INSIDE && currentState == GeofenceState.OUTSIDE -> GeofenceTransition.EXITED
            else -> GeofenceTransition.NO_CHANGE
        }

        val alertTriggered = (transition == GeofenceTransition.ENTERED)
        val radiusLabel = if (event.geofenceRadiusMeters % 1.0 == 0.0) {
            "${event.geofenceRadiusMeters.toInt()}m"
        } else {
            "${event.geofenceRadiusMeters}m"
        }
        val message = if (alertTriggered) {
            "Hóspede entrou no raio de $radiusLabel da propriedade."
        } else {
            "Posição atualizada sem alerta."
        }

        return GeofenceEvaluationResult(
            correlationId = correlationId,
            hotelId = event.hotelId,
            distanceMeters = roundedDistance,
            currentState = currentState,
            transition = transition,
            alertTriggered = alertTriggered,
            message = message
        )
    }
}
