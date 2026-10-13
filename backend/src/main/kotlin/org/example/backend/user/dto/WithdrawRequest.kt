package org.example.backend.user.dto

import java.math.BigDecimal

data class WithdrawRequest(
  val currencyId: Long,
  val amount: BigDecimal
)
