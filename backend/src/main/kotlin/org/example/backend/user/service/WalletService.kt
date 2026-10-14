package org.example.backend.user.service

import java.math.BigDecimal

interface WalletService {
    fun connectAccount(userId: Long, bankId: Long, accountNum: String)
    fun checkBalance(userId: Long, currencyId: Long): BigDecimal
    fun depositFromAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal
    fun withdrawToAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal
}
