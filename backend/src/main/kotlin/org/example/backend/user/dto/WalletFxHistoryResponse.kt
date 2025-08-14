package org.example.backend.user.dto

import org.example.backend.enums.WalletFxHistoryType
import java.math.BigDecimal
import java.time.LocalDateTime

data class WalletFxHistoryResponse(
  val id: Long,
  val walletId: Long,
  val currencyId: Long,
  val orderId: Long?,
  val amount: BigDecimal,
  val balanceAfter: BigDecimal,
  val type: WalletFxHistoryType,
  val executedAt: LocalDateTime
)
