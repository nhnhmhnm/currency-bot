package org.example.backend.user.dto

import org.example.backend.enums.TransferReason
import java.math.BigDecimal

data class MoneyMovementCommand(
    val source: BalanceRef,
    val target: BalanceRef,
    val currencyId: Long,
    val amount: BigDecimal,
    val orderId: Long?,
    val reason: TransferReason
)
