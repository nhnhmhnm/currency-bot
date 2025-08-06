package org.example.backend.exception

import org.springframework.http.HttpStatus

enum class ErrorCode (
  val httpStatus: HttpStatus,
  val code: Int,
  val message: String
) {
  DUPLICATED_USER_DEVICE(HttpStatus.BAD_REQUEST, 101, "duplicated user device"),
  FAILED_TO_CREATE_USER_ID(HttpStatus.INTERNAL_SERVER_ERROR, 102, "failed to create user id")
}