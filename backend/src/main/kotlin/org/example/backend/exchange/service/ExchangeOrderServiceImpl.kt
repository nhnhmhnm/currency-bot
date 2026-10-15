package org.example.backend.exchange.service

import org.example.backend.common.util.toDto
import org.example.backend.enums.ExchangeLedgerType
import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.domain.ExchangeReservation
import org.example.backend.exchange.domain.ReservationStatus
import org.example.backend.exchange.dto.ExchangeLedgerCommand
import org.example.backend.exchange.dto.ExchangeOrderResponse
import org.example.backend.exchange.dto.TransactionCommand
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.exchange.repository.ExchangeReservationRepository
import org.example.backend.exchange.repository.OutboxEventRepository
import org.example.backend.exchange.domain.OutboxEvent
import org.example.backend.enums.OutboxEventType
import org.example.backend.finance.domain.Currency
import org.example.backend.exchange.dto.BestExchangeRateDto
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Service
class ExchangeOrderServiceImpl(
    private val exchangeOrderRepository: ExchangeOrderRepository,
    private val exchangeService: ExchangeService,
    private val transactionService: TransactionService,
    private val ledgerService: ExchangeLedgerService,
    private val currencyRepository: CurrencyRepository,
    private val postingService: PostingService,
    private val requestValidator: ExchangeOrderRequestValidator,
    private val outboxEventRepository: OutboxEventRepository,
    private val reservationRepository: ExchangeReservationRepository,
    private val operations: ExchangeOperationsSnapshotService
) : ExchangeOrderService {
    private val log = LoggerFactory.getLogger(ExchangeOrderServiceImpl::class.java)

    private val terminalStatuses = setOf(
        OrderStatus.SETTLED,
        OrderStatus.SUCCESS,
        OrderStatus.FAILED,
        OrderStatus.CANCELED,
        OrderStatus.COMPENSATED
    )

    @Transactional(readOnly = true)
    override fun getOrder(userId: Long, orderId: Long): ExchangeOrderResponse {
        val order = exchangeOrderRepository.findById(orderId).orElse(null)
        if (order == null || order.userId != userId) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return order.toDto()
    }

    @Transactional
    override fun requestBuyOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String?,
        expectedRate: BigDecimal?
    ): ExchangeOrderResponse {
        return createQueuedOrder(
            userId = userId,
            orderType = OrderType.BUY,
            fromCurrencyCode = "KRW",
            toCurrencyCode = currencyCode,
            fromAmount = fromAmount,
            idempotencyKey = idempotencyKey,
            expectedRate = expectedRate
        ).toDto()
    }

    @Transactional
    override fun requestSellOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String?,
        expectedRate: BigDecimal?
    ): ExchangeOrderResponse {
        return createQueuedOrder(
            userId = userId,
            orderType = OrderType.SELL,
            fromCurrencyCode = currencyCode,
            toCurrencyCode = "KRW",
            fromAmount = fromAmount,
            idempotencyKey = idempotencyKey,
            expectedRate = expectedRate
        ).toDto()
    }

    @Transactional
    override fun requestArbitrageOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String?,
        expectedRate: BigDecimal?,
        expectedCounterRate: BigDecimal?
    ): ExchangeOrderResponse {
        return createQueuedOrder(
            userId = userId,
            orderType = OrderType.ARBITRAGE,
            fromCurrencyCode = "KRW",
            toCurrencyCode = currencyCode,
            fromAmount = fromAmount,
            idempotencyKey = idempotencyKey,
            expectedRate = expectedRate,
            expectedCounterRate = expectedCounterRate
        ).toDto()
    }

    @Transactional
    override fun processQueuedOrder(orderId: Long): ExchangeOrderResponse {
        val order = operations.measureLockWait { exchangeOrderRepository.findByIdForUpdate(orderId) }
            ?: error("Exchange order not found: $orderId")

        if (order.status in terminalStatuses) {
            return order.toDto()
        }

        order.nextAttemptAt?.let { due ->
            if (due.isAfter(Instant.now())) throw OrderRetryNotDueException(due)
        }

        operations.measureLockWait { requestValidator.lockUserForProcessing(order.userId) }

        val currency = getCurrency(if (order.orderType == OrderType.SELL) order.fromCurrencyId else order.toCurrencyId)
        val rate = when (order.orderType) {
            OrderType.BUY -> exchangeService.getBestBuyRate(currency.code)
            OrderType.SELL -> exchangeService.getBestSellRate(currency.code)
            OrderType.ARBITRAGE -> exchangeService.getBestBuyBaseRate(currency.code)
        }
        val counterRate = if (order.orderType == OrderType.ARBITRAGE) {
            exchangeService.getBestSellBaseRate(currency.code)
        } else null
        val quotedRate = order.quotedRate
        if (quotedRate != null && (quotedRate.compareTo(rate.bestRate) != 0 ||
                (counterRate != null && order.quotedCounterRate?.compareTo(counterRate.bestRate) != 0))) {
            order.status = OrderStatus.CANCELED
            order.failureReason = "RATE_CHANGED"
            order.completedAt = LocalDateTime.now()
            transitionReservation(order, ReservationStatus.RELEASED)
            return order.toDto()
        }

        order.status = OrderStatus.PROCESSING
        order.processedAt = LocalDateTime.now()
        order.nextAttemptAt = null

        return when (order.orderType) {
            OrderType.BUY -> executeBuyOrder(order, isArbitrage = false, bestRate = rate)
            OrderType.SELL -> executeSellOrder(order, isArbitrage = false, bestRate = rate)
            OrderType.ARBITRAGE -> executeArbitrageParentOrder(order, rate, requireNotNull(counterRate))
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun recordProcessingFailure(
        orderId: Long,
        failureId: String,
        reason: String,
        maxAttempts: Int
    ): OrderRetryDecision {
        require(maxAttempts > 0)
        val order = exchangeOrderRepository.findByIdForUpdate(orderId)
            ?: return OrderRetryDecision(terminal = true)
        if (order.status in terminalStatuses) return OrderRetryDecision(terminal = true)
        if (order.lastFailureId == failureId) {
            return OrderRetryDecision(terminal = order.status == OrderStatus.FAILED, nextAttemptAt = order.nextAttemptAt)
        }

        order.lastFailureId = failureId
        order.retryAttempts += 1
        order.failureStage = "ORDER_WORKER"
        order.failureReason = reason.take(255)

        if (order.retryAttempts >= maxAttempts) {
            order.status = OrderStatus.FAILED
            order.completedAt = LocalDateTime.now()
            order.nextAttemptAt = null
            transitionReservation(order, ReservationStatus.RELEASED)
            return OrderRetryDecision(terminal = true)
        }

        val delay = Duration.ofSeconds(1L shl order.retryAttempts.coerceAtMost(6))
        order.nextAttemptAt = Instant.now().plus(delay).truncatedTo(ChronoUnit.MILLIS)
        order.status = OrderStatus.QUEUED
        return OrderRetryDecision(terminal = false, nextAttemptAt = order.nextAttemptAt)
    }

    private fun executeArbitrageParentOrder(
        order: ExchangeOrder,
        buyRate: BestExchangeRateDto,
        sellRate: BestExchangeRateDto
    ): ExchangeOrderResponse {
        val currency = currencyRepository.findById(order.toCurrencyId)
            .orElseThrow { IllegalStateException("Currency not found: ${order.toCurrencyId}") }

        val buy = executeBuyOrder(
            createProcessingOrder(order.userId, OrderType.BUY, "KRW", currency.code, order.fromAmount),
            isArbitrage = true,
            bestRate = buyRate
        )
        val sell = executeSellOrder(
            createProcessingOrder(order.userId, OrderType.SELL, currency.code, "KRW", buy.toAmount),
            isArbitrage = true,
            bestRate = sellRate
        )

        order.bankId = sell.bankId
        order.exchangeRate = sell.exchangeRate
        order.toAmount = sell.toAmount
        order.status = OrderStatus.SETTLED
        order.completedAt = LocalDateTime.now()
        transitionReservation(order, ReservationStatus.CONSUMED)
        saveCompletedEvent(order)

        log.info(
            "arbitrage parent order[{}] settled by buyOrder={}, sellOrder={}",
            order.id,
            buy.id,
            sell.id
        )

        return order.toDto()
    }

    private fun executeBuyOrder(
        order: ExchangeOrder,
        isArbitrage: Boolean,
        bestRate: BestExchangeRateDto
    ): ExchangeOrderResponse {
        val fromCurrency = getCurrency(order.fromCurrencyId)
        val toCurrency = getCurrency(order.toCurrencyId)
        require(order.fromAmount >= bestRate.bestRate) {
            "Minimum exchange amount not met for ${toCurrency.code}"
        }

        order.bankId = bestRate.bankId
        order.exchangeRate = bestRate.bestRate

        val commissionAmount = ExchangeCommissionPolicy.calculate(
            baseAmount = order.fromAmount,
            scale = fromCurrency.scale,
            isArbitrage = isArbitrage
        )
        val exchangeAmount = order.fromAmount.minus(commissionAmount)
        val (toAmount, bankProfit) = exchangeService.calculateExchange(
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            exchangeRate = bestRate.bestRate,
            fromAmount = exchangeAmount
        )

        order.toAmount = toAmount

        postingService.post(
            PostingPlan.buy(
                orderId = order.id!!,
                userId = order.userId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = order.fromAmount,
                exchangeAmount = exchangeAmount,
                toAmount = toAmount,
                commissionAmount = commissionAmount
            )
        )

        recordTransactions(
            userId = order.userId,
            companyUserId = 1L,
            orderId = order.id!!,
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            fromAmount = order.fromAmount,
            exchangeAmount = exchangeAmount,
            toAmount = toAmount,
            rawToAmount = toAmount,
            exchangeRate = bestRate.bestRate,
            commissionCurrencyId = fromCurrency.id,
            commissionAmount = commissionAmount,
            profitCurrencyId = toCurrency.id,
            bankProfit = bankProfit
        )

        ledgerService.record(
            ExchangeLedgerCommand(
                userId = order.userId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = order.fromAmount,
                toAmount = toAmount,
                exchangeRate = bestRate.bestRate,
                commissionCurrencyId = fromCurrency.id,
                commissionRate = ExchangeCommissionPolicy.RATE,
                commissionAmount = commissionAmount,
                type = ExchangeLedgerType.FX_BUY
            )
        )

        order.status = OrderStatus.SETTLED
        order.completedAt = LocalDateTime.now()
        if (!isArbitrage) {
            transitionReservation(order, ReservationStatus.CONSUMED)
            saveCompletedEvent(order)
        }

        return order.toDto()
    }

    private fun executeSellOrder(
        order: ExchangeOrder,
        isArbitrage: Boolean,
        bestRate: BestExchangeRateDto
    ): ExchangeOrderResponse {
        val fromCurrency = getCurrency(order.fromCurrencyId)
        val toCurrency = getCurrency(order.toCurrencyId)
        require(order.fromAmount >= fromCurrency.unit) {
            "Minimum exchange amount not met for ${fromCurrency.code}"
        }

        order.bankId = bestRate.bankId
        order.exchangeRate = bestRate.bestRate

        val (rawToAmount, bankProfit) = exchangeService.calculateExchange(
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            exchangeRate = bestRate.bestRate,
            fromAmount = order.fromAmount
        )
        val commissionAmount = ExchangeCommissionPolicy.calculate(
            baseAmount = rawToAmount,
            scale = toCurrency.scale,
            isArbitrage = false
        )
        val toAmount = rawToAmount.minus(commissionAmount)

        order.toAmount = toAmount

        postingService.post(
            PostingPlan.sell(
                orderId = order.id!!,
                userId = order.userId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = order.fromAmount,
                rawToAmount = rawToAmount,
                toAmount = toAmount,
                commissionAmount = commissionAmount
            )
        )

        recordTransactions(
            userId = order.userId,
            companyUserId = 1L,
            orderId = order.id!!,
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            fromAmount = order.fromAmount,
            exchangeAmount = order.fromAmount,
            toAmount = toAmount,
            rawToAmount = rawToAmount,
            exchangeRate = bestRate.bestRate,
            commissionCurrencyId = toCurrency.id,
            commissionAmount = commissionAmount,
            profitCurrencyId = toCurrency.id,
            bankProfit = bankProfit
        )

        ledgerService.record(
            ExchangeLedgerCommand(
                userId = order.userId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = order.fromAmount,
                toAmount = toAmount,
                exchangeRate = bestRate.bestRate,
                commissionCurrencyId = toCurrency.id,
                commissionRate = ExchangeCommissionPolicy.RATE,
                commissionAmount = commissionAmount,
                type = ExchangeLedgerType.FX_SELL
            )
        )

        order.status = OrderStatus.SETTLED
        order.completedAt = LocalDateTime.now()
        if (!isArbitrage) {
            transitionReservation(order, ReservationStatus.CONSUMED)
            saveCompletedEvent(order)
        }

        return order.toDto()
    }

    private fun createQueuedOrder(
        userId: Long,
        orderType: OrderType,
        fromCurrencyCode: String,
        toCurrencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String?,
        expectedRate: BigDecimal?,
        expectedCounterRate: BigDecimal? = null
    ): ExchangeOrder {
        requestValidator.lockUser(userId)
        val fromCurrency = currencyRepository.findByCode(fromCurrencyCode.uppercase())
        val toCurrency = currencyRepository.findByCode(toCurrencyCode.uppercase())

        if (!idempotencyKey.isNullOrBlank()) {
            exchangeOrderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.let { existingOrder ->
                validateIdempotentRequest(existingOrder, orderType, fromCurrency, toCurrency, fromAmount)
                return existingOrder
            }
        }

        val businessDate = java.time.LocalDate.now(SEOUL_ZONE)
        if (orderType == OrderType.ARBITRAGE && fromAmount.compareTo(ArbitrageQuotePolicy.AMOUNT_KRW) != 0) {
            throw UserException(ErrorCode.ARBITRAGE_AMOUNT_EXCEEDED)
        }
        val arbitrageSlot = if (orderType == OrderType.ARBITRAGE) {
            val occupied = exchangeOrderRepository.findOccupiedArbitrageSlots(userId, businessDate).toSet()
            (1..ArbitrageQuotePolicy.DAILY_LIMIT).firstOrNull { it !in occupied }
                ?: throw UserException(ErrorCode.ARBITRAGE_DAILY_LIMIT_EXCEEDED)
        } else null
        requestValidator.validateAfterUserLock(
            userId = userId,
            orderType = orderType,
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            fromAmount = fromAmount
        )

        val quote = when (orderType) {
            OrderType.BUY -> exchangeService.getBestBuyRate(toCurrency.code)
            OrderType.SELL -> exchangeService.getBestSellRate(fromCurrency.code)
            OrderType.ARBITRAGE -> exchangeService.getBestBuyBaseRate(toCurrency.code)
        }
        val counterQuote = if (orderType == OrderType.ARBITRAGE) {
            exchangeService.getBestSellBaseRate(toCurrency.code)
        } else null
        if (expectedRate != null && expectedRate.compareTo(quote.bestRate) != 0 ||
            expectedCounterRate != null && (counterQuote == null ||
                expectedCounterRate.compareTo(counterQuote.bestRate) != 0)) {
            throw UserException(ErrorCode.RATE_CHANGED)
        }
        if (orderType != OrderType.SELL && fromAmount < quote.bestRate) {
            throw UserException(ErrorCode.INVALID_EXCHANGE_AMOUNT)
        }
        if (orderType == OrderType.ARBITRAGE && ArbitrageQuotePolicy.estimate(
                fromCurrency, toCurrency, quote.bestRate,
                requireNotNull(counterQuote).bestRate, exchangeService
            ).profitKrw < BigDecimal.ZERO) {
            throw UserException(ErrorCode.ARBITRAGE_NOT_PROFITABLE)
        }

        return saveOrder(
            userId = userId,
            orderType = orderType,
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            fromAmount = fromAmount,
            status = OrderStatus.QUEUED,
            idempotencyKey = idempotencyKey,
            quotedRate = quote.bestRate,
            quotedCounterRate = counterQuote?.bestRate,
            businessDate = businessDate,
            arbitrageSlot = arbitrageSlot
        )
    }

    private fun validateIdempotentRequest(
        existingOrder: ExchangeOrder,
        orderType: OrderType,
        fromCurrency: Currency,
        toCurrency: Currency,
        fromAmount: BigDecimal
    ) {
        val sameRequest = existingOrder.orderType == orderType &&
            existingOrder.fromCurrencyId == fromCurrency.id &&
            existingOrder.toCurrencyId == toCurrency.id &&
            existingOrder.fromAmount.compareTo(fromAmount) == 0

        if (!sameRequest) {
            throw org.example.backend.exception.UserException(
                org.example.backend.exception.ErrorCode.IDEMPOTENCY_KEY_CONFLICT
            )
        }
    }

    private fun createProcessingOrder(
        userId: Long,
        orderType: OrderType,
        fromCurrencyCode: String,
        toCurrencyCode: String,
        fromAmount: BigDecimal
    ): ExchangeOrder {
        val fromCurrency = currencyRepository.findByCode(fromCurrencyCode.uppercase())
        val toCurrency = currencyRepository.findByCode(toCurrencyCode.uppercase())

        return saveOrder(
            userId = userId,
            orderType = orderType,
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            fromAmount = fromAmount,
            status = OrderStatus.PROCESSING,
            idempotencyKey = null
        ).also { it.processedAt = LocalDateTime.now() }
    }

    private fun saveOrder(
        userId: Long,
        orderType: OrderType,
        fromCurrency: Currency,
        toCurrency: Currency,
        fromAmount: BigDecimal,
        status: OrderStatus,
        idempotencyKey: String?,
        quotedRate: BigDecimal? = null,
        quotedCounterRate: BigDecimal? = null,
        businessDate: java.time.LocalDate? = null,
        arbitrageSlot: Int? = null
    ): ExchangeOrder {
        val order = exchangeOrderRepository.save(
            ExchangeOrder(
                userId = userId,
                bankId = 0L,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = fromAmount,
                toAmount = BigDecimal.ZERO,
                exchangeRate = BigDecimal.ZERO,
                quotedRate = quotedRate,
                quotedCounterRate = quotedCounterRate,
                arbitrageSlotDate = businessDate.takeIf { orderType == OrderType.ARBITRAGE && status == OrderStatus.QUEUED },
                arbitrageSlotNumber = arbitrageSlot,
                orderType = orderType,
                status = status,
                idempotencyKey = idempotencyKey
            )
        )

        if (status == OrderStatus.QUEUED) {
            reservationRepository.save(
                ExchangeReservation(
                    orderId = order.id!!,
                    userId = userId,
                    fromCurrencyId = fromCurrency.id,
                    fromAmount = fromAmount,
                    businessDate = requireNotNull(businessDate)
                )
            )
            outboxEventRepository.save(
                OutboxEvent(
                    aggregateId = order.id!!,
                    eventType = OutboxEventType.ORDER_ACCEPTED
                )
            )
        }

        return order
    }

    private fun getCurrency(currencyId: Long): Currency {
        return currencyRepository.findById(currencyId)
            .orElseThrow { IllegalStateException("Currency not found: $currencyId") }
    }

    private fun saveCompletedEvent(order: ExchangeOrder) {
        outboxEventRepository.save(
            OutboxEvent(aggregateId = order.id!!, eventType = OutboxEventType.ORDER_COMPLETED)
        )
    }

    private fun transitionReservation(order: ExchangeOrder, target: ReservationStatus) {
        val reservation = reservationRepository.findById(order.id!!).orElse(null) ?: run {
            check(order.quotedRate == null) { "Reservation missing for accepted order ${order.id}" }
            return
        }
        check(reservation.status == ReservationStatus.ACTIVE || reservation.status == target) {
            "Cannot change reservation ${order.id} from ${reservation.status} to $target"
        }
        reservation.status = target
        if (target == ReservationStatus.RELEASED) {
            order.arbitrageSlotDate = null
            order.arbitrageSlotNumber = null
        }
    }

    companion object {
        private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }

    private fun recordTransactions(
        userId: Long,
        companyUserId: Long,
        orderId: Long,
        fromCurrency: Currency,
        toCurrency: Currency,
        fromAmount: BigDecimal,
        exchangeAmount: BigDecimal,
        toAmount: BigDecimal,
        rawToAmount: BigDecimal,
        exchangeRate: BigDecimal,
        commissionCurrencyId: Long,
        commissionAmount: BigDecimal,
        profitCurrencyId: Long,
        bankProfit: BigDecimal
    ) {
        transactionService.record(
            TransactionCommand(
                userId = userId,
                orderId = orderId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = fromAmount,
                toAmount = toAmount,
                exchangeRate = exchangeRate,
                commissionCurrencyId = commissionCurrencyId,
                commissionAmount = commissionAmount,
                profitCurrencyId = null,
                profit = null
            )
        )

        transactionService.record(
            TransactionCommand(
                userId = companyUserId,
                orderId = orderId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = exchangeAmount,
                toAmount = rawToAmount,
                exchangeRate = exchangeRate,
                commissionCurrencyId = null,
                commissionAmount = null,
                profitCurrencyId = profitCurrencyId,
                profit = bankProfit
            )
        )
    }
}
