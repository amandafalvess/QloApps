package com.hotel.location.exception

enum class DomainErrorCode {
    INVALID_COORDINATES,
    INVALID_RADIUS,
    INVALID_STATE,
    MISSING_FIELD
}

abstract class DomainException(
    override val message: String,
    val field: String? = null,
    val errorCode: DomainErrorCode
) : RuntimeException(message)

class InvalidCoordinatesException(detail: String, field: String = "coordinates") : DomainException(
    message = detail,
    field = field,
    errorCode = DomainErrorCode.INVALID_COORDINATES
)

class InvalidGeofenceRadiusException(radius: Double) : DomainException(
    message = "O raio da geocerca deve ser estritamente maior que zero (recebido: $radius).",
    field = "geofence_radius_m",
    errorCode = DomainErrorCode.INVALID_RADIUS
)

class InvalidGeofenceStateException(state: String) : DomainException(
    message = "O estado '$state' é inválido. Valores aceitos: 'inside' ou 'outside'.",
    field = "previous_state",
    errorCode = DomainErrorCode.INVALID_STATE
)

class MissingFieldException(fieldName: String) : DomainException(
    message = "O campo '$fieldName' é obrigatório.",
    field = fieldName,
    errorCode = DomainErrorCode.MISSING_FIELD
)
