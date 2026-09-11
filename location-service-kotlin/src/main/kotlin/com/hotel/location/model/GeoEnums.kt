package com.hotel.location.model

import com.hotel.location.exception.InvalidGeofenceStateException

enum class GeofenceState {
    INSIDE,
    OUTSIDE;

    companion object {
        fun fromString(value: String): GeofenceState {
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: throw InvalidGeofenceStateException(value)
        }
    }
}

enum class GeofenceTransition {
    ENTERED,
    EXITED,
    NO_CHANGE
}
