package org.example.backend.user.service

import java.math.BigDecimal

interface WalletService {
    fun connectAccount(userId: Long, bankId: Long, accountNum: String) // 지갑에 계좌 연결

    fun checkBalance(userId: Long, currencyId: Long): BigDecimal // 현재 잔액 리턴
    fun depositFromAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal // 연결된 계좌에서 SUPER 계좌로 입금 -> 확인되면 지갑에 입금
    fun withdrawToAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal // 지갑에서 연결된 계좌로 출금
}
