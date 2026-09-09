package com.hotel.location

import com.hotel.location.dto.GeofenceErrorLog
import com.hotel.location.dto.HealthResponse
import com.hotel.location.dto.LocationEventRequestDto
import com.hotel.location.dto.ProblemDetailsResponse
import com.hotel.location.dto.toDto
import com.hotel.location.dto.toLog
import com.hotel.location.exception.InvalidContentTypeException
import com.hotel.location.exception.InvalidHeaderException
import com.hotel.location.exception.LocationValidationException
import com.hotel.location.exception.MissingContentTypeException
import com.hotel.location.exception.MissingHeaderException
import com.hotel.location.exception.ServiceUnavailableException
import com.hotel.location.service.HaversineEngine
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun main() {
    embeddedServer(Netty, port = 8104, host = "127.0.0.1", module = Application::module).start(wait = true)
}

private val logger = LoggerFactory.getLogger("com.hotel.location.Application")

private val UUID_V4_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

fun isValidUuid(value: String): Boolean = UUID_V4_REGEX.matches(value.trim())

var isServiceAvailable: Boolean = true

fun resetServiceState() {
    isServiceAvailable = true
}

private fun extractFieldFromSerializationMessage(message: String?): String? {
    if (message == null) return null
    val fieldMatch = Regex("Field '([^']+)'").find(message)
    if (fieldMatch != null) return fieldMatch.groupValues[1]
    val pathMatch = Regex("path: \\$\\.([a-zA-Z0-9_]+)").find(message)
    if (pathMatch != null) return pathMatch.groupValues[1]
    return null
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(StatusPages) {
        exception<LocationValidationException> { call, cause ->
            val correlationId = call.request.headers["X-Correlation-ID"] ?: "none"
            val errorLog = GeofenceErrorLog(
                timestamp = java.time.Instant.now().toString(),
                level = "WARN",
                correlation_id = correlationId,
                event = "GEOFENCE_VALIDATION_FAILED",
                error_type = cause.typeUri,
                status_code = cause.statusCode.value,
                path = call.request.path(),
                message = cause.message,
                field = cause.field,
                code = cause.errorCode.name
            )
            logger.warn(Json.encodeToString(errorLog))
            call.respond(
                cause.statusCode,
                ProblemDetailsResponse(
                    type = cause.typeUri,
                    title = cause.title,
                    status = cause.statusCode.value,
                    detail = cause.message,
                    instance = call.request.path(),
                    code = cause.errorCode.name
                )
            )
        }

        exception<SerializationException> { call, cause ->
            val correlationId = call.request.headers["X-Correlation-ID"] ?: "none"
            val field = extractFieldFromSerializationMessage(cause.message)
            val detail = if (field != null) {
                "O campo '$field' contém dados inválidos ou incompatíveis."
            } else {
                "O payload enviado é um JSON malformado ou incompatível."
            }
            val errorLog = GeofenceErrorLog(
                timestamp = java.time.Instant.now().toString(),
                level = "WARN",
                correlation_id = correlationId,
                event = "MALFORMED_JSON_ERROR",
                error_type = "urn:problem-type:malformed-json",
                status_code = HttpStatusCode.BadRequest.value,
                path = call.request.path(),
                message = detail,
                field = field,
                code = "MALFORMED_JSON"
            )
            logger.warn(Json.encodeToString(errorLog))
            call.respond(
                HttpStatusCode.BadRequest,
                ProblemDetailsResponse(
                    type = "urn:problem-type:malformed-json",
                    title = "Malformed JSON Request",
                    status = HttpStatusCode.BadRequest.value,
                    detail = detail,
                    instance = call.request.path(),
                    code = "MALFORMED_JSON"
                )
            )
        }

        exception<BadRequestException> { call, cause ->
            val correlationId = call.request.headers["X-Correlation-ID"] ?: "none"
            val isSerialization = cause.cause is SerializationException ||
                cause.cause?.cause is SerializationException ||
                cause.message?.contains("convert", ignoreCase = true) == true ||
                cause.message?.contains("json", ignoreCase = true) == true ||
                cause.cause?.javaClass?.simpleName?.contains("Json", ignoreCase = true) == true ||
                cause.cause?.javaClass?.simpleName?.contains("Convert", ignoreCase = true) == true

            val typeUri = if (isSerialization) "urn:problem-type:malformed-json" else "urn:problem-type:bad-request"
            val title = if (isSerialization) "Malformed JSON Request" else "Bad Request"
            val code = if (isSerialization) "MALFORMED_JSON" else "BAD_REQUEST"

            val field = extractFieldFromSerializationMessage(cause.cause?.message ?: cause.message)
            val detail = if (field != null) {
                "O campo '$field' contém dados inválidos."
            } else if (isSerialization) {
                "O payload enviado é um JSON malformado ou incompatível."
            } else {
                "Requisição inválida."
            }

            val errorLog = GeofenceErrorLog(
                timestamp = java.time.Instant.now().toString(),
                level = "WARN",
                correlation_id = correlationId,
                event = if (isSerialization) "MALFORMED_JSON_ERROR" else "BAD_REQUEST_ERROR",
                error_type = typeUri,
                status_code = HttpStatusCode.BadRequest.value,
                path = call.request.path(),
                message = detail,
                field = field,
                code = code
            )
            logger.warn(Json.encodeToString(errorLog))
            call.respond(
                HttpStatusCode.BadRequest,
                ProblemDetailsResponse(
                    type = typeUri,
                    title = title,
                    status = HttpStatusCode.BadRequest.value,
                    detail = detail,
                    instance = call.request.path(),
                    code = code
                )
            )
        }

        exception<ServiceUnavailableException> { call, cause ->
            val correlationId = call.request.headers["X-Correlation-ID"] ?: "none"
            val errorLog = GeofenceErrorLog(
                timestamp = java.time.Instant.now().toString(),
                level = "ERROR",
                correlation_id = correlationId,
                event = "GEOFENCE_SERVICE_UNAVAILABLE",
                error_type = cause.typeUri,
                status_code = cause.statusCode.value,
                path = call.request.path(),
                message = cause.message,
                field = cause.field,
                code = cause.errorCode.name
            )
            logger.error(Json.encodeToString(errorLog))
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ProblemDetailsResponse(
                    type = cause.typeUri,
                    title = cause.title,
                    status = HttpStatusCode.ServiceUnavailable.value,
                    detail = cause.message,
                    instance = call.request.path(),
                    code = cause.errorCode.name
                )
            )
        }

        exception<Throwable> { call, cause ->
            val correlationId = call.request.headers["X-Correlation-ID"] ?: "none"
            val errorLog = GeofenceErrorLog(
                timestamp = java.time.Instant.now().toString(),
                level = "ERROR",
                correlation_id = correlationId,
                event = "INTERNAL_SERVER_ERROR",
                error_type = "urn:problem-type:internal-server-error",
                status_code = HttpStatusCode.InternalServerError.value,
                path = call.request.path(),
                message = cause.message ?: "Erro interno inesperado no servidor.",
                code = "INTERNAL_SERVER_ERROR"
            )
            logger.error(Json.encodeToString(errorLog))
            call.respond(
                HttpStatusCode.InternalServerError,
                ProblemDetailsResponse(
                    type = "urn:problem-type:internal-server-error",
                    title = "Internal Server Error",
                    status = HttpStatusCode.InternalServerError.value,
                    detail = "Erro interno inesperado no servidor.",
                    instance = call.request.path(),
                    code = "INTERNAL_SERVER_ERROR"
                )
            )
        }
    }

    routing {
        get("/healthz") {
            if (!isServiceAvailable) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    HealthResponse(
                        status = "DOWN",
                        service = "location-service-kotlin",
                        port = 8104
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.OK,
                    HealthResponse(
                        status = "UP",
                        service = "location-service-kotlin",
                        port = 8104
                    )
                )
            }
        }
        post("/v1/location-events") {
            if (!isServiceAvailable || call.request.headers["X-Mock-Service-Unavailable"]?.equals("true", ignoreCase = true) == true) {
                throw ServiceUnavailableException()
            }

            val rawContentType = call.request.headers[HttpHeaders.ContentType]
            if (rawContentType.isNullOrBlank()) {
                throw MissingContentTypeException()
            }
            val parsedContentType = runCatching { ContentType.parse(rawContentType) }.getOrNull()
            if (parsedContentType == null || parsedContentType.withoutParameters() != ContentType.Application.Json) {
                throw InvalidContentTypeException(rawContentType)
            }

            val correlationId = call.request.headers["X-Correlation-ID"]
            if (correlationId.isNullOrBlank()) {
                throw MissingHeaderException("X-Correlation-ID")
            }
            if (!isValidUuid(correlationId)) {
                throw InvalidHeaderException("X-Correlation-ID", "O valor '$correlationId' não é um UUID v4 válido.")
            }

            val startTimeNano = System.nanoTime()
            val requestDto = call.receive<LocationEventRequestDto>()
            val domainEvent = requestDto.toDomain()
            val result = HaversineEngine.evaluate(domainEvent, correlationId)
            val durationMs = (System.nanoTime() - startTimeNano) / 1_000_000.0

            val logEvent = result.toLog(durationMs)
            logger.info(Json.encodeToString(logEvent))

            call.respond(HttpStatusCode.OK, result.toDto())
        }
    }
}
