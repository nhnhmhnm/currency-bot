package org.example.backend.user.dto

import org.example.backend.user.type.UserType

data class UserSignupRequest(
  val device: String,
  val password: String,
  val name: String,
  val phone: String,
  val type: UserType,
)