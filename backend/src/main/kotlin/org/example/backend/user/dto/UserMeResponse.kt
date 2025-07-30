package org.example.backend.user.dto

import org.example.backend.enums.UserType
import java.time.LocalDateTime

data class UserMeResponse(
  val id: Long,
  val device: String,
  val name: String,
  val phone: String,
  val type: UserType,
  val isActive: Boolean,
  val createdAt: LocalDateTime
)