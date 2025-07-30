package org.example.backend.exception

enum class ErrorCode (
  val code: Int,
  val message: String
) {
  DUPLICATED_USER_DEVICE(101, "duplicated user device"),
  FAILED_TO_CREATE_USER_ID(102, "failed to create user id")
}