package org.example.backend.user.service

import java.math.BigDecimal

interface WalletService {
    fun connectAccount(userId: Long, bankId: Long, accountNum: String) // 지갑에 계좌 연결

    fun checkBalance(userId: Long, currencyId: Long): BigDecimal // 현재 잔액 리턴
    fun depositFromAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal // 연결된 계좌에서 SUPER 계좌로 입금 -> 확인되면 지갑에 입금
    fun withdrawToAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal // 지갑에서 연결된 계좌로 출금

    // 회사 계좌 -> 유저 지갑
    fun companyToUser(accountId: Long, userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal

    // 유저 지갑 -> 회사 계좌
    fun userToCompany(userId: Long, currencyId: Long, accountId: Long, amount: BigDecimal): BigDecimal

    // 회사 계좌 -> 회사 계좌
    // sell 후 환전된 금액에서 수수료를 계산하여 회사 수수료 계좌로 입금
    fun companyToCompany(fromAccountId: Long, toAccountId: Long, amount: BigDecimal): BigDecimal

    // 은행-회사 사이에서 환전한 금액을 회사 계좌로 입금
    fun bankToCompany(accountId: Long, bankId: Long, amount: BigDecimal): BigDecimal
}
