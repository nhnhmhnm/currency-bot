package org.example.backend.user.dto

import org.example.backend.user.type.UserType
import java.time.LocalDateTime

data class UserSignupResponse(
  val id: Long,
  val device: String,
  val name: String,
  val phone: String,
  val type: UserType,
  val isActive: Boolean = true,
  val createdAt: LocalDateTime
)