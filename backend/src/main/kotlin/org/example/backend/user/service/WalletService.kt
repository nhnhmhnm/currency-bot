package org.example.backend.user.service

import java.math.BigDecimal

interface WalletService {
    fun connectAccount(userId: Long, bankId: Long, accountNum: String) // 지갑에 계좌 연결

    fun checkBalance(userId: Long, currencyId: Long): BigDecimal // 현재 잔액 리턴
    fun depositFromAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal // 연결된 계좌에서 SUPER 계좌로 입금 -> 확인되면 지갑에 입금
    fun withdrawToAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal // 지갑에서 연결된 계좌로 출금


    /**
     회사 계좌 -> 유저 지갑 depositFromAccount

     buy
    최종 환전된 금액 toAmount

    sell
    유저 최종 환전 금액 toAmount
     **/
    fun companyToUser(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal

    /**
     유저 지갑 -> 회사 계좌 withdrawToAccount

    buy
    수수료 commissionAmount
    실제 환전할 원화 exchangeAmount

    sell
    환전할 달러 toAmount

     **/
    fun userToCompany(userId: Long, currencyId: Long, accountId: Long, amount: BigDecimal): BigDecimal

    // 회사 계좌 -> 회사 계좌
    // sell 후 환전된 금액에서 수수료, 차익을 계산하여 회사 계좌로 입금
    fun companyToCompany(fromAccountId: Long, toAccountId: Long, amount: BigDecimal): BigDecimal

    /*
    은행 -> 회사 계좌
    // 회사 계좌 잔액 증가/ 감소
    // userid, accountid, amount

    buy
     환전된 금액 toAmount 증가/감소

     sell
     환전할 달러 toAmount 증가/감소
    환전된 금액 rawToAmount
     */

    // 은행에서 환전한 금액을 회사 계좌로 입금
    fun bankToCompany(accountId: Long, bankId: Long, amount: BigDecimal): BigDecimal


}
