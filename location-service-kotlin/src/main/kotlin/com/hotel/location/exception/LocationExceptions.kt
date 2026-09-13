package com.hotel.location.exception

import io.ktor.http.HttpStatusCode

enum class LocationErrorCode {
    INVALID_COORDINATES,
    INVALID_RADIUS,
    INVALID_STATE,
    MISSING_HEADER,
    INVALID_HEADER,
    UNSUPPORTED_MEDIA_TYPE,
    MISSING_FIELD,
    MALFORMED_JSON,
    BAD_REQUEST,
    SERVICE_UNAVAILABLE,
    INTERNAL_SERVER_ERROR
}

abstract class LocationValidationException(
    val typeUri: String,
    val title: String,
    override val message: String,
    val statusCode: HttpStatusCode = HttpStatusCode.BadRequest,
    val field: String? = null,
    val errorCode: LocationErrorCode
) : RuntimeException(message)

class MissingHeaderException(headerName: String) : LocationValidationException(
    typeUri = "urn:problem-type:missing-header",
    title = "Missing Required Header",
    message = "O header obrigatório '$headerName' não foi fornecido.",
    field = headerName,
    errorCode = LocationErrorCode.MISSING_HEADER
)

class InvalidHeaderException(headerName: String, detail: String) : LocationValidationException(
    typeUri = "urn:problem-type:invalid-header",
    title = "Invalid Header",
    message = "O header '$headerName' é inválido: $detail",
    field = headerName,
    errorCode = LocationErrorCode.INVALID_HEADER
)

class MissingContentTypeException : LocationValidationException(
    typeUri = "urn:problem-type:unsupported-media-type",
    title = "Unsupported Media Type",
    message = "O header 'Content-Type' é obrigatório e deve ser 'application/json'.",
    statusCode = HttpStatusCode.UnsupportedMediaType,
    field = "Content-Type",
    errorCode = LocationErrorCode.UNSUPPORTED_MEDIA_TYPE
)

class InvalidContentTypeException(contentType: String) : LocationValidationException(
    typeUri = "urn:problem-type:unsupported-media-type",
    title = "Unsupported Media Type",
    message = "O Content-Type deve ser 'application/json' (recebido: '$contentType').",
    statusCode = HttpStatusCode.UnsupportedMediaType,
    field = "Content-Type",
    errorCode = LocationErrorCode.UNSUPPORTED_MEDIA_TYPE
)


class ServiceUnavailableException(
    detail: String = "O serviço de cálculo de geofencing está temporariamente indisponível."
) : LocationValidationException(
    typeUri = "urn:problem-type:service-unavailable",
    title = "Service Unavailable",
    message = detail,
    statusCode = HttpStatusCode.ServiceUnavailable,
    errorCode = LocationErrorCode.SERVICE_UNAVAILABLE
)

