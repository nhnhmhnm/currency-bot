package org.example.backend.exception

import org.springframework.http.HttpStatus

class UserException (
  val status: HttpStatus,
  val errorCode: Int,
  override val message: String?,
) : RuntimeException() {
    constructor(errorCode: ErrorCode, message: String? = null)
            : this(errorCode.httpStatus, errorCode.code, message ?: errorCode.message)
}