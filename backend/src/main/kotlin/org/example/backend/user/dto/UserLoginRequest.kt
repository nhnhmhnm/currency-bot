package org.example.backend.user.dto

data class UserLoginRequest(
  val device: String,
  val password: String,
)