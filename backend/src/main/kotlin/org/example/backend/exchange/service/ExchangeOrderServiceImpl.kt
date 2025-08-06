package org.example.backend.exchange.service

import jakarta.transaction.Transactional
import org.example.backend.common.util.toDTO
import org.example.backend.enums.ExchangeLedgerType
import org.example.backend.enums.OrderStatus
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.dto.ExchangeLedgerDTO
import org.example.backend.exchange.dto.ExchangeOrderDTO
import org.example.backend.exchange.dto.TransactionDTO
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.finance.repository.CurrencyRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
open class ExchangeOrderServiceImpl(
    private val exchangeOrderRepository: ExchangeOrderRepository,
    private val exchangeService: ExchangeService,
    private val transactionService: TransactionService,
    private val ledgerService: ExchangeLedgerService,
    private val currencyRepository: CurrencyRepository
): ExchangeOrderService {
    @Transactional
    override fun buyOrder(userId: Long, currencyCode: String, amount: BigDecimal): ExchangeOrderDTO {
        // 1. PENDING 주문 생성
        val fromCurrencyId = currencyRepository.findByCode("KRW")?.id
            ?: throw IllegalArgumentException("KRW 통화 없음")
        val toCurrencyId = currencyRepository.findByCode(currencyCode)?.id
            ?: throw IllegalArgumentException("통화 없음: $currencyCode")

        var order = ExchangeOrder(
                userId = userId,
                bankId = 0L, // 2번에서
                fromCurrencyId = fromCurrencyId,
                toCurrencyId = fromCurrencyId,
                fromAmount = amount,
                toAmount = null, // 3번에서
                exchangeRate = null, // 2번에서
                status = OrderStatus.PENDING, // 마지막에 변경
                statusDesc = null, // 주문 상태 설명 실패일 때만 작성
                requestedAt = LocalDateTime.now(),
                executedAt = null // 마지막에 변경
            )

        val savedOrder = exchangeOrderRepository.save(order)

        try {
            // 2. 최적 은행/환율 조회
            val bestRate = exchangeService.getBestBuyRate(currencyCode)
            savedOrder.bankId = bestRate.bankId
            savedOrder.exchangeRate = bestRate.bestRate

            // 3. toAmount 계산 + 가상 금액 이동

            // 4. 주문 SUCCESS로 변경
            savedOrder.status = OrderStatus.SUCCESS
            savedOrder.executedAt = LocalDateTime.now()

            exchangeOrderRepository.save(savedOrder)

            // 5. 거래/장부 기록 저장 (간단 예시)
            val trxDto = TransactionDTO(
                userId = userId,
                walletId = 0L,
                orderId = savedOrder.id!!,
                fromCurrencyId = fromCurrencyId,
                toCurrencyId = toCurrencyId,
                fromAmount = amount,
                toAmount = savedOrder.toAmount,
                exchangeRate = savedOrder.exchangeRate,
                commissionRate = null,
                commissionAmount = null,
                commissionCurrencyId = null,
                profit = null,
                profitCurrencyId = null
            )
            transactionService.record(trxDto)

            val ledgerDto = ExchangeLedgerDTO(
                userId = userId,
                walletId = 0L,
                currencyId = toCurrencyId,
                amount = savedOrder.toAmount,
                balance = BigDecimal.ZERO,
                exchangeRate = savedOrder.exchangeRate!!,
                commissionAmount = null,
                commissionRate = null,
                commissionCurrencyId = null,
                type = ExchangeLedgerType.FX_BUY
            )
            ledgerService.record(ledgerDto)

            return savedOrder.toDTO()

        } catch (ex: Exception) {
            // 실패시 주문 FAILED
            savedOrder.status = OrderStatus.FAILED
            // 실패 이유
//            savedOrder.statudDesc =
            exchangeOrderRepository.save(savedOrder)
            throw ex
        }
    }
    @Transactional
    override fun sellOrder(userId: Long, currencyCode: String, amount: BigDecimal): ExchangeOrderDTO {
        val fromCurrencyId = currencyRepository.findByCode(currencyCode)?.id
            ?: throw IllegalArgumentException("통화 없음: $currencyCode")
        val toCurrencyId = currencyRepository.findByCode("KRW")?.id
            ?: throw IllegalArgumentException("KRW 통화 없음")

        var order = ExchangeOrder(
            userId = userId,
            bankId = 0L, // 최적 은행
            fromCurrencyId = fromCurrencyId,
            toCurrencyId = toCurrencyId,
            fromAmount = amount,
            toAmount = null,
            exchangeRate = null,
            status = OrderStatus.PENDING,
            statusDesc = null,
            requestedAt = LocalDateTime.now(),
            executedAt = null
        )

        var savedOrder = exchangeOrderRepository.save(order)

        try {
            // 2. 최적 은행/환율 조회 (판매)
            val bestRate = exchangeService.getBestSellRate(currencyCode)
            savedOrder.bankId = bestRate.bankId
            savedOrder.exchangeRate = bestRate.bestRate

            // 3. toAmount 계산 (외화 * 환율)
            savedOrder.status = OrderStatus.SUCCESS
            savedOrder.executedAt = LocalDateTime.now()

            exchangeOrderRepository.save(savedOrder)


            // 4. 거래/장부 기록 저장
            val trxDto = TransactionDTO(
                userId = userId,
                walletId = 0L,
                orderId = savedOrder.id!!,
                fromCurrencyId = fromCurrencyId,
                toCurrencyId = toCurrencyId,
                fromAmount = amount,
                toAmount = savedOrder.toAmount,
                exchangeRate = savedOrder.exchangeRate,
                commissionRate = null,
                commissionAmount = null,
                commissionCurrencyId = null,
                profit = null,
                profitCurrencyId = null
            )
            transactionService.record(trxDto)

            val ledgerDto = ExchangeLedgerDTO(
                userId = userId,
                walletId = 0L,
                currencyId = fromCurrencyId,
                amount = amount.negate(), // 외화가 빠져나감
                balance = BigDecimal.ZERO,
                exchangeRate = savedOrder.exchangeRate!!,
                commissionAmount = null,
                commissionRate = null,
                commissionCurrencyId = null,
                type = ExchangeLedgerType.FX_SELL
            )
            ledgerService.record(ledgerDto)

            return savedOrder.toDTO()

        } catch (ex: Exception) {
            savedOrder.status = OrderStatus.FAILED
            exchangeOrderRepository.save(savedOrder)
            throw ex
        }
    }

    @Transactional
    override fun arbitrageOrder(userId: Long, currencyCode: String, amount: BigDecimal): Pair<ExchangeOrderDTO, ExchangeOrderDTO> {
        // 1. BuyOrder 실행: KRW → 외화
        val buyOrderDto = buyOrder(userId, currencyCode, amount)

        // 2. SellOrder 실행: 외화 → KRW (amount : 방금 매수한 외화)
        val sellOrderDto = sellOrder(userId, currencyCode, buyOrderDto.toAmount!!)

        // 3. Pair로 반환
        return Pair(buyOrderDto, sellOrderDto)
    }
}