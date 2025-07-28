package org.example.backend.exception

import org.springframework.http.HttpStatus

class UserException (
  val status: HttpStatus,
  val errorCode: ErrorCode
) : RuntimeException(errorCode.message)