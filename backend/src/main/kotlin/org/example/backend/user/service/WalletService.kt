package org.example.backend.user.service

import org.example.backend.user.dto.AccountRegistrationRequest
import java.math.BigDecimal

interface WalletService {
    fun registerAccount(userId: Long, request: AccountRegistrationRequest) // 지갑에 계좌 등록

    fun checkBalance(userId: Long, currencyId: Long): BigDecimal // 현재 잔액 리턴
    fun increase(userId: Long, currencyId: Long, amount: BigDecimal) // 최종 잔액 리턴
    fun decrease(userId: Long, currencyId: Long, amount: BigDecimal) // 최종 잔액 리턴
}
