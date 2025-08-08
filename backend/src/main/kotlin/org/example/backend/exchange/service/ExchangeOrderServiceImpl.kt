package org.example.backend.exchange.service

import org.example.backend.common.util.toDTO
import org.example.backend.enums.ExchangeLedgerType
import org.example.backend.enums.OrderStatus
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.dto.ExchangeLedgerDTO
import org.example.backend.exchange.dto.ExchangeOrderDTO
import org.example.backend.exchange.dto.TransactionDTO
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

@Service
class ExchangeOrderServiceImpl(
    private val exchangeOrderRepository: ExchangeOrderRepository,
    private val exchangeService: ExchangeService,
    private val transactionService: TransactionService,
    private val ledgerService: ExchangeLedgerService,
    private val currencyRepository: CurrencyRepository,
    private val walletRepository: WalletRepository
): ExchangeOrderService {
    val commission = BigDecimal("0.0005") // 0.05% 수수료

    @Transactional
    override fun buyOrder(userId: Long, currencyCode: String, amount: BigDecimal, isArbitrage: Boolean): ExchangeOrderDTO {
        val requestedAt = LocalDateTime.now()

        // 통화 조회
        val fromCurrency = currencyRepository.findByCode("KRW")
        val toCurrency = currencyRepository.findByCode(currencyCode)

        // 환전 계산
        val bestRate = if (isArbitrage) exchangeService.getBestBuyBaseRate(currencyCode) else exchangeService.getBestBuyRate(currencyCode)

        val commissionAmount = amount.multiply(commission) // 환전 수수료
        val exchangeAmount = amount - commissionAmount // 실제 환전할 금액

        val (toAmount, bankProfit) = exchangeService.calculateExchange(
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            exchangeRate = bestRate.bestRate,
            fromAmount = exchangeAmount,
        )

        val order = ExchangeOrder(
                userId = userId,
                bankId = bestRate.bankId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = amount,
                toAmount = toAmount,
                exchangeRate = bestRate.bestRate,
                status = OrderStatus.PENDING,
                requestedAt = requestedAt,
            )

        exchangeOrderRepository.save(order)

        // 가상 금액 이동
        val userWallet = walletRepository.findByUserIdAndCurrencyId(userId, fromCurrency.id)
        val companyWallet = walletRepository.findByUserIdAndCurrencyId(1L, fromCurrency.id)

        // 4. 주문 SUCCESS 변경
        val savedOrder = exchangeOrderRepository.findById(order.id)
            .orElseThrow { IllegalArgumentException("주문을 찾을 수 없습니다: ${order.id}") }
            .apply {
                status = OrderStatus.SUCCESS
            }

        // 5. 거래 기록 저장
        val userToCompany = TransactionDTO(
            userId = userId,
            walletId = userWallet.id, // 사용자 지갑 ID
            orderId = savedOrder.id,
            fromCurrencyId = fromCurrency.id,
            toCurrencyId = toCurrency.id,
            fromAmount = amount,
            toAmount = toAmount,
            exchangeRate = bestRate.bestRate,
            commissionAmount = commissionAmount, // commission : 유저가 앱에서 환전을 할 때 발생하는 수수료 (회사가 받는 이익)
            commissionCurrencyId = fromCurrency.id,
            profitCurrencyId = toCurrency.id,
            profit = null
        )
        transactionService.record(userToCompany)

        val companyToBank = TransactionDTO(
            userId = 1L, // 회사 ID
            walletId = companyWallet.id, // 회사 지갑 ID
            orderId = savedOrder.id,
            fromCurrencyId = fromCurrency.id,
            toCurrencyId = toCurrency.id,
            fromAmount = exchangeAmount,
            toAmount = toAmount,
            exchangeRate = bestRate.bestRate,
            commissionCurrencyId = fromCurrency.id,
            commissionAmount = null,
            profitCurrencyId = toCurrency.id,
            profit = bankProfit // profit : 환전으로 인해 소수점 이하가 발생하는 경우, 그 차액 (은행이 받는 이익)
        )
        transactionService.record(companyToBank)

        // 6. 환전 장부 기록 저장
        val ledgerDto = ExchangeLedgerDTO(
            userId = userId,
            fromCurrencyId = fromCurrency.id,
            toCurrencyId = toCurrency.id,
            fromAmount = amount,
            toAmount = toAmount,
            exchangeRate = bestRate.bestRate,
            commissionCurrencyId = fromCurrency.id,
            commissionAmount = commissionAmount,
            type = ExchangeLedgerType.FX_BUY,
        )
        ledgerService.record(ledgerDto)

        return savedOrder.toDTO()
    }

    @Transactional
    override fun sellOrder(userId: Long, currencyCode: String, amount: BigDecimal, isArbitrage: Boolean): ExchangeOrderDTO {
        val requestedAt = LocalDateTime.now()

        // 통화 조회
        val fromCurrency = currencyRepository.findByCode(currencyCode)
        val toCurrency = currencyRepository.findByCode("KRW")

        // 환전 계산
        val bestRate =
            if (isArbitrage) {
                exchangeService.getBestSellBaseRate(currencyCode)
            }
            else {
                exchangeService.getBestSellRate(currencyCode)
            }

        val (rawToAmount, bankProfit) = exchangeService.calculateExchange(
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            exchangeRate = bestRate.bestRate,
            fromAmount = amount,
        )

        // 수수료 계산
        val commissionAmount = if (isArbitrage) BigDecimal.ZERO else rawToAmount.multiply(commission)
        val tmpToAmount = rawToAmount.subtract(commissionAmount)

        val toAmount = tmpToAmount.setScale(toCurrency.scale, RoundingMode.DOWN)
        val profit = tmpToAmount.subtract(toAmount)

        val order = ExchangeOrder(
            userId = userId,
            bankId = bestRate.bankId,
            fromCurrencyId = fromCurrency.id,
            toCurrencyId = toCurrency.id,
            fromAmount = amount,
            toAmount = toAmount,
            exchangeRate = bestRate.bestRate,
            status = OrderStatus.PENDING,
            requestedAt = requestedAt,
        )

        exchangeOrderRepository.save(order)

        // 가상 금액 이동
        val userWallet = walletRepository.findByUserIdAndCurrencyId(userId, fromCurrency.id)
        val companyWallet = walletRepository.findByUserIdAndCurrencyId(1L, toCurrency.id)

        // 4. 주문 SUCCESS 변경
        val savedOrder = exchangeOrderRepository.findById(order.id)
            .orElseThrow { IllegalArgumentException("주문을 찾을 수 없습니다: ${order.id}") }
            .apply {
                status = OrderStatus.SUCCESS
            }

        // 5. 거래 기록 저장
        val userToCompany = TransactionDTO(
            userId = userId,
            walletId = userWallet.id, // 사용자 지갑 ID
            orderId = savedOrder.id,
            fromCurrencyId = fromCurrency.id,
            toCurrencyId = toCurrency.id,
            fromAmount = amount,
            toAmount = toAmount,
            exchangeRate = bestRate.bestRate,
            commissionAmount = commissionAmount, // commission : 유저가 앱에서 환전을 할 때 발생하는 수수료 (회사가 받는 이익)
            commissionCurrencyId = fromCurrency.id,
            profitCurrencyId = toCurrency.id,
            profit = profit
        )
        transactionService.record(userToCompany)

        val companyToBank = TransactionDTO(
            userId = 1L, // 회사 ID
            walletId = companyWallet.id, // 회사 지갑 ID
            orderId = savedOrder.id,
            fromCurrencyId = fromCurrency.id,
            toCurrencyId = toCurrency.id,
            fromAmount = amount,
            toAmount = rawToAmount,
            exchangeRate = bestRate.bestRate,
            commissionCurrencyId = fromCurrency.id,
            commissionAmount = null,
            profitCurrencyId = toCurrency.id,
            profit = bankProfit // profit : 환전으로 인해 소수점 이하가 발생하는 경우, 그 차액 (은행이 받는 이익)
        )
        transactionService.record(companyToBank)

        // 6. 환전 장부 기록 저장
        val ledgerDto = ExchangeLedgerDTO(
            userId = userId,
            fromCurrencyId = fromCurrency.id,
            toCurrencyId = toCurrency.id,
            fromAmount = amount,
            toAmount = toAmount,
            exchangeRate = bestRate.bestRate,
            commissionCurrencyId = fromCurrency.id,
            commissionAmount = commissionAmount,
            type = ExchangeLedgerType.FX_SELL,
        )
        ledgerService.record(ledgerDto)

        return savedOrder.toDTO()
    }

    @Transactional
    override fun arbitrageOrder(userId: Long, currencyCode: String, amount: BigDecimal, isArbitrage: Boolean): Pair<ExchangeOrderDTO, ExchangeOrderDTO> {
        // 1. BuyOrder 실행: KRW → 외화
        val buyOrderDto = buyOrder(userId, currencyCode, amount, true)

        // 2. SellOrder 실행: 외화 → KRW (amount : 방금 매수한 외화)
        val sellOrderDto = sellOrder(userId, currencyCode, buyOrderDto.toAmount, true)

        // 3. Pair 반환
        return Pair(buyOrderDto, sellOrderDto)
    }
}
