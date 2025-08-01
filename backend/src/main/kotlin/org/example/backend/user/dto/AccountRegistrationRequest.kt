package org.example.backend.user.dto

data class AccountRegistrationRequest(
  // userId는 SecurityContext에서 가져옴
  val bankId: Long,
  val currencyId: Long,
  val accountNum: String
)
