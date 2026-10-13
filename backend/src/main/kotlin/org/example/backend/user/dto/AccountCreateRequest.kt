package org.example.backend.user.dto

data class AccountCreateRequest(
  val bankId: Long,
  val currencyId: Long,
  val accountNum: String
)
