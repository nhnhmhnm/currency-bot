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
  ACCOUNT_INACTIVE(HttpStatus.BAD_REQUEST, 308, "account is inactive"),
  BANK_INTEGRATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, 309, "bank transfer is unavailable"),
  SIMULATED_BANK_BALANCE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, 310, "simulated bank balance not found"),
  COMPANY_LIQUIDITY_INSUFFICIENT(HttpStatus.SERVICE_UNAVAILABLE, 311, "company liquidity is insufficient"),

  // CURRENCY 400
  CURRENCY_MISMATCH(HttpStatus.BAD_REQUEST, 401, "currency mismatch"),
  INVALID_TRANSFER_AMOUNT(HttpStatus.BAD_REQUEST, 402, "transfer amount must be positive and match currency scale"),

  // EXCHANGE ORDER 500
  INVALID_EXCHANGE_AMOUNT(HttpStatus.BAD_REQUEST, 501, "exchange amount must be positive"),
  DAILY_EXCHANGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, 502, "daily exchange limit exceeded"),
  IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, 503, "idempotency key was already used for another request"),
}
