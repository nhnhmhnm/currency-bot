package org.example.backend.user.dto

import org.example.backend.enums.WalletTransactionType
import java.math.BigDecimal

data class DepositWithdrawalCommand(
  val userId : Long,
  
  val walletId : Long,
  val currencyId : Long,
  val amount : BigDecimal,
  val type: WalletTransactionType
)