package org.example.backend.user.dto

data class AccountConnectRequest(
  val bankId: Long,
  val accountNum: String
)
