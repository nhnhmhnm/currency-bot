package org.example.backend.exception

import org.springframework.http.HttpStatus

enum class ErrorCode (
  val httpStatus: HttpStatus,
  val code: Int,
  val message: String
) {
  // USER 100
  DUPLICATED_USER_DEVICE(HttpStatus.BAD_REQUEST, 101, "duplicated user device"),
  FAILED_TO_CREATE_USER_ID(HttpStatus.INTERNAL_SERVER_ERROR, 102, "failed to create user id"),
  USER_NOT_FOUND(HttpStatus.BAD_REQUEST, 103, "user not found"),
  INCORRECT_PASSWORD(HttpStatus.BAD_REQUEST, 104, "incorrect password"),

  // WALLET 200
  WALLET_NOT_FOUND(HttpStatus.BAD_REQUEST, 201, "wallet not found"),
  INSUFFICIENT_WALLET_BALANCE(HttpStatus.BAD_REQUEST, 202, "insufficient wallet balance"),

  // ACCOUNT 300
  DUPLICATED_ACCOUNT(HttpStatus.BAD_REQUEST, 301, "duplicated account"),
  ACCOUNT_NOT_FOUND(HttpStatus.BAD_REQUEST, 302, "account not found"),
  INCORRECT_OWNER(HttpStatus.BAD_REQUEST, 303, "incorrect owner"),
  ACCOUNT_NOT_CONNECTED(HttpStatus.BAD_REQUEST, 304, "account not connected"),
  SUPER_ACCOUNT_NOT_FOUND(HttpStatus.BAD_REQUEST, 305, "super account not found"),
  INSUFFICIENT_ACCOUNT_BALANCE(HttpStatus.BAD_REQUEST, 306, "insufficient balance"),
  COMPANY_ACCOUNT_NOT_FOUND(HttpStatus.BAD_REQUEST, 307, "company account not found"),

  // CURRENCY 400
  CURRENCY_MISMATCH(HttpStatus.BAD_REQUEST, 401, "currency mismatch"),
}
