package org.example.backend.user.dto

import java.math.BigDecimal

data class WithdrawRequest(
  val userId: Long,
  val currencyId: Long,
  val amount: BigDecimal
)
