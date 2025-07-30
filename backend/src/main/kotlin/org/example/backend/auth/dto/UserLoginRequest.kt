package org.example.backend.auth.dto

data class UserLoginRequest(
  val device: String,
  val password: String,
)