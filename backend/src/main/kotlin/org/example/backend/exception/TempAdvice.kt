package org.example.backend.exception

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import kotlin.code

class TempAdvice {
    // user exception
    @ExceptionHandler(UserException::class)
    fun handleUserException(ex: UserException): ResponseEntity<HttpResponse> {
        val locErrorMessage = let {
            val msg = i18nLocale.getMessage("exception_${ex.code}")
            msg.ifEmpty {
                ex.message
            }
        }
        logger.info { "Exception: UserException : status(${ex.status}) code(${ex.code}) / msg(${locErrorMessage})" }
        ex.log?.run {
            logger.info { "Exception log : $this" }
        }
        return responseEntity(ex.status, ex.code, locErrorMessage, ex.message)
    }
}