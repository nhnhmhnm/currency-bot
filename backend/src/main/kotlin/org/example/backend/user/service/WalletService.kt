package org.example.backend.user.service

import java.math.BigDecimal

interface WalletService {
    fun connectAccount(userId: Long, bankId: Long, accountNum: String) // 지갑에 계좌 연결

    fun checkBalance(userId: Long, currencyId: Long): BigDecimal // 현재 잔액 리턴
    fun increase(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal // 증액 후 최종 잔액 리턴
    fun decrease(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal // 감액 후 최종 잔액 리턴
}
