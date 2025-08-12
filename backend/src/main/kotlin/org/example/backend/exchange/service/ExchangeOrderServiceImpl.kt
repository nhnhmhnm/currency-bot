package org.example.backend.exchange.service

import org.example.backend.common.util.toDTO
import org.example.backend.enums.ExchangeLedgerType
import org.example.backend.enums.OrderStatus
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.dto.ExchangeLedgerCommand
import org.example.backend.exchange.dto.ExchangeOrderResponse
import org.example.backend.exchange.dto.TransactionCommand
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.user.repository.WalletRepository
import org.example.backend.user.service.WalletService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ExchangeOrderServiceImpl(
    private val exchangeOrderRepository: ExchangeOrderRepository,
    private val exchangeService: ExchangeService,
    private val transactionService: TransactionService,
    private val ledgerService: ExchangeLedgerService,
    private val currencyRepository: CurrencyRepository,
    private val walletRepository: WalletRepository,
    private val walletService: WalletService
): ExchangeOrderService {
    val commission = BigDecimal("0.0005") // 0.05% 수수료

    @Transactional
    override fun buyOrder(userId: Long, currencyCode: String, amount: BigDecimal, isArbitrage: Boolean): ExchangeOrderResponse {
        // 통화 조회
        val fromCurrency = currencyRepository.findByCode("KRW")
        val toCurrency = currencyRepository.findByCode(currencyCode)

        // 환전 계산
        val bestRate = if (isArbitrage) exchangeService.getBestBuyBaseRate(currencyCode) else exchangeService.getBestBuyRate(currencyCode)

        val order = ExchangeOrder(
                userId = userId,
                bankId = bestRate.bankId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = amount,
                toAmount = BigDecimal.ZERO, // 임시값
                exchangeRate = bestRate.bestRate,
                status = OrderStatus.PENDING,
        )

        val savedOrder = exchangeOrderRepository.save(order) // 영속 상태
        val orderId: Long = savedOrder.id ?: error("Order ID is null")

        return try {
            val commissionAmount = if(isArbitrage) BigDecimal.ZERO else amount.times(commission) // 환전 수수료
            val exchangeAmount = amount.minus(commissionAmount) // 실제 환전할 금액

            val (toAmount, bankProfit) = exchangeService.calculateExchange(
                fromCurrency = fromCurrency,
                toCurrency = toCurrency,
                exchangeRate = bestRate.bestRate,
                fromAmount = exchangeAmount,
            )


            val userWallet = walletRepository.findByUserIdAndCurrencyId(userId, fromCurrency.id)
            val companyWallet = walletRepository.findByUserIdAndCurrencyId(1L, fromCurrency.id)

            // 3. 가상 금액 이동

            // 유저 원화 지갑 -> 회사 원화 계좌 exchangeAmount, 회사 수수료 계좌 commissionAmount

            // 회사 원화 계좌 -> 은행

            // 은행 -> 회사 외화 계좌 toAmount, 은행 차익 bankProfit

            // 회사 외화 계좌 -> 유저 외화 지갑 toAmount


            // 4. 더티 체킹으로 주문 SUCCESS 업데이트
            savedOrder.status = OrderStatus.SUCCESS

            // 5. 거래 기록 저장
            val userToCompany = TransactionCommand(
                userId = userId,
//                walletId = userWallet.id, // 사용자 지갑 ID
                orderId = orderId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = amount,
                toAmount = toAmount,
                exchangeRate = bestRate.bestRate,
                commissionAmount = commissionAmount, // commission : 유저가 앱에서 환전을 할 때 발생하는 수수료 (회사가 받는 이익)
                commissionCurrencyId = fromCurrency.id,
                profitCurrencyId = toCurrency.id,
                profit = BigDecimal.ZERO
            )
            transactionService.record(userToCompany)

            val companyToBank = TransactionCommand(
                userId = 1L, // 회사 ID
//                walletId = companyWallet.id, // 회사 지갑 ID
                orderId = orderId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = exchangeAmount,
                toAmount = toAmount,
                exchangeRate = bestRate.bestRate,

                // 회사-은행간의 수수료 미정
                commissionCurrencyId = null,
                commissionAmount = null,

                profitCurrencyId = toCurrency.id,
                profit = bankProfit // profit : 환전으로 인해 소수점 이하가 발생하는 경우, 그 차액 (은행이 받는 이익)
            )
            transactionService.record(companyToBank)

            // 6. 환전 장부 기록 저장
            val ledgerDto = ExchangeLedgerCommand(
                userId = userId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = amount,
                toAmount = toAmount,
                exchangeRate = bestRate.bestRate,
                commissionCurrencyId = fromCurrency.id,
                commissionRate = commission,
                commissionAmount = commissionAmount,
                type = ExchangeLedgerType.FX_BUY,
            )
            ledgerService.record(ledgerDto)

            return savedOrder.toDTO()
        } catch (e: Exception) {
            // 주문 실패 시, 주문 상태를 FAILED로 변경
            savedOrder.status = OrderStatus.FAILED

            savedOrder.toDTO()
        }
    }

    @Transactional
    override fun sellOrder(userId: Long, currencyCode: String, amount: BigDecimal, isArbitrage: Boolean): ExchangeOrderResponse {
        // 통화 조회
        val fromCurrency = currencyRepository.findByCode(currencyCode)
        val toCurrency = currencyRepository.findByCode("KRW")

        val bestRate = if (isArbitrage) exchangeService.getBestSellBaseRate(currencyCode) else exchangeService.getBestSellRate(currencyCode)

        val order = ExchangeOrder(
            userId = userId,
            bankId = bestRate.bankId,
            fromCurrencyId = fromCurrency.id,
            toCurrencyId = toCurrency.id,
            fromAmount = amount,
            toAmount = BigDecimal.ZERO, // 임시값
            exchangeRate = bestRate.bestRate,
            status = OrderStatus.PENDING,
        )
        val savedOrder = exchangeOrderRepository.save(order)
        val orderId: Long = savedOrder.id ?: error("Order ID is null")

        return try {
            val (rawToAmount, bankProfit) = exchangeService.calculateExchange(
                fromCurrency = fromCurrency,
                toCurrency = toCurrency,
                exchangeRate = bestRate.bestRate,
                fromAmount = amount,
            )

            // 수수료 계산
//            val commissionAmount = if (isArbitrage) BigDecimal.ZERO else rawToAmount.multiply(commission)
            val commissionAmount = rawToAmount.times(commission)  // 환전 수수료
            val tmpToAmount = rawToAmount.minus(commissionAmount) // 환전된 금액 - 수수료

            val toAmount = tmpToAmount.setScale(toCurrency.scale, RoundingMode.DOWN) // 유저가 실제 받는 금액
            val profit = tmpToAmount.minus(toAmount) // 회사 차익

            val userWallet = walletRepository.findByUserIdAndCurrencyId(userId, fromCurrency.id)
            val companyWallet = walletRepository.findByUserIdAndCurrencyId(1L, toCurrency.id)

            // 3. 가상 금액 이동

            // 유저 외화 지갑 -> 회사 외화 계좌 amount

            // 회사 외화 계좌 -> 은행 amount

            // 은행 -> 회사 원화 계좌 rawToAmount, 은행 차익 bankProfit

            // 회사 원화 계좌 -> 회사 수수료 계좌 commissionAmount, 회사 차익 계좌 profit, 유저 원화 지갑 toAmount

            // 4. 더티 체킹으로 주문 SUCCESS 업데이트
            savedOrder.status = OrderStatus.SUCCESS

            // 5. 거래 기록 저장
            val userToCompany = TransactionCommand(
                userId = userId,
//                walletId = userWallet.id, // 사용자 지갑 ID
                orderId = orderId,
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

            val companyToBank = TransactionCommand(
                userId = 1L, // 회사 ID
//                walletId = companyWallet.id, // 회사 지갑 ID
                orderId = orderId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = amount,
                toAmount = rawToAmount,
                exchangeRate = bestRate.bestRate,

                // 회사-은행간의 수수료 미정
                commissionCurrencyId = null,
                commissionAmount = null,

                profitCurrencyId = toCurrency.id,
                profit = bankProfit // profit : 환전으로 인해 소수점 이하가 발생하는 경우, 그 차액 (은행이 받는 이익)
            )
            transactionService.record(companyToBank)

            // 6. 환전 장부 기록 저장
            val ledgerDto = ExchangeLedgerCommand(
                userId = userId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = amount,
                toAmount = toAmount,
                exchangeRate = bestRate.bestRate,
                commissionCurrencyId = fromCurrency.id,
                commissionRate = commission,
                commissionAmount = commissionAmount,
                type = ExchangeLedgerType.FX_SELL,
            )
            ledgerService.record(ledgerDto)

            return savedOrder.toDTO()
        } catch (e: Exception) {
            // 주문 실패 시, 주문 상태를 FAILED로 변경
            savedOrder.status = OrderStatus.FAILED

            savedOrder.toDTO()
        }
    }

    @Transactional
    override fun arbitrageOrder(userId: Long, currencyCode: String, amount: BigDecimal, isArbitrage: Boolean): Pair<ExchangeOrderResponse, ExchangeOrderResponse> {
        // 1. BuyOrder 실행: KRW → 외화
        val buy = buyOrder(userId, currencyCode, amount, true)

        if (buy.status != OrderStatus.SUCCESS) {
            // 매수 실패/취소면 매도는 수행하지 않고 바로 리턴
            return Pair(buy, buy)
        }
        // 2. SellOrder 실행: 외화 → KRW (amount : 방금 매수한 외화)
        val sell = sellOrder(userId, currencyCode, buy.toAmount, true)

        return Pair(buy, sell)
    }
}
