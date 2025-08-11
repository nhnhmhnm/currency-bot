package org.example.backend.user.service

import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

interface WalletService {
    fun connectAccount(userId: Long, bankId: Long, accountNum: String) // 지갑에 계좌 연결

    fun checkBalance(userId: Long, currencyId: Long): BigDecimal // 현재 잔액 리턴
    fun depositFromAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal // 연결된 계좌에서 SUPER 계좌로 입금 -> 확인되면 지갑에 입금
    fun withdrawToAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal // 지갑에서 연결된 계좌로 출금

    /**
     * FX 매수(KRW -> 외화) 정산:
     * - 사용자 KRW 지갑에서 amount 차감
     * - 회사 KRW 지갑에 commissionAmount 가산(수수료 수익)
     * - 사용자 외화 지갑에 toAmount 가산
     */
    @Transactional
    fun settleFxBuy(
        orderId: Long,
        userId: Long,
        fromCurrencyId: Long, // KRW
        toCurrencyId: Long,   // 외화
        amount: BigDecimal,           // 사용자가 낸 총 KRW
        commissionAmount: BigDecimal, // 수수료 KRW
        toAmount: BigDecimal          // 사용자에게 지급할 외화
    )

    /**
     * FX 매도(외화 -> KRW) 정산:
     * - 사용자 외화 지갑에서 amount 차감
     * - 회사 KRW 지갑에 commissionAmount 가산(수수료 수익)
     * - 사용자 KRW 지갑에 toAmount 가산
     */
    @Transactional
    fun settleFxSell(
        orderId: Long,
        userId: Long,
        fromCurrencyId: Long, // 외화
        toCurrencyId: Long,   // KRW
        amount: BigDecimal,           // 사용자가 판 외화
        commissionAmount: BigDecimal, // 수수료 KRW(차감 방식이면 0 가능)
        toAmount: BigDecimal          // 사용자에게 지급할 KRW
    )
}
