package org.example.backend.auth.dto

data class TokenResponse(
  val accessToken: String,
  val refreshToken: String
)