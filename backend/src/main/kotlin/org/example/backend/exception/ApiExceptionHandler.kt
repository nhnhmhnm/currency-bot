package org.example.backend.exception

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

data class ApiErrorResponse(
    val timestamp: LocalDateTime,
    val status: Int,
    val code: Int,
    val message: String,
    val path: String
)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(UserException::class)
    fun handleUserException(
        exception: UserException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val body = ApiErrorResponse(
            timestamp = LocalDateTime.now(),
            status = exception.status.value(),
            code = exception.errorCode,
            message = exception.message.orEmpty(),
            path = request.requestURI
        )

        return ResponseEntity.status(exception.status).body(body)
    }
}
