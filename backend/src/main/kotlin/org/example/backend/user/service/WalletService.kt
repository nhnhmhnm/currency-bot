package org.example.backend.user.service

import java.math.BigDecimal

interface WalletService {
    fun checkBalance(userId: Long, currencyCode: String): BigDecimal // 현재 잔액 리턴
    fun increase(userId: Long, currencyCode: String, amount: BigDecimal) // 최종 잔액 리턴
    fun decrease(userId: Long, currencyCode: String, amount: BigDecimal) // 최종 잔액 리턴
}
