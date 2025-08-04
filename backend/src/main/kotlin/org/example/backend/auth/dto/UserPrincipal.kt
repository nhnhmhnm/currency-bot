package org.example.backend.auth.dto

import org.example.backend.enums.UserType

data class UserPrincipal(
  val id: Long,
  val name: String,
  val type: UserType
)
