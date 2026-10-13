package org.example.backend.exchange.service

import org.example.backend.common.util.toDto
import org.example.backend.enums.CompanyAccount
import org.example.backend.enums.ExchangeLedgerType
import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.dto.ExchangeLedgerCommand
import org.example.backend.exchange.dto.ExchangeOrderResponse
import org.example.backend.exchange.dto.TransactionCommand
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.exchange.repository.OutboxEventRepository
import org.example.backend.exchange.domain.OutboxEvent
import org.example.backend.enums.OutboxEventType
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.domain.InternalAccount
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.finance.repository.InternalAccountRepository
import org.example.backend.user.service.WalletService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class ExchangeOrderServiceImpl(
    private val exchangeOrderRepository: ExchangeOrderRepository,
    private val exchangeService: ExchangeService,
    private val transactionService: TransactionService,
    private val ledgerService: ExchangeLedgerService,
    private val currencyRepository: CurrencyRepository,
    private val walletService: WalletService,
    private val internalAccountRepository: InternalAccountRepository,
    private val requestValidator: ExchangeOrderRequestValidator,
    private val outboxEventRepository: OutboxEventRepository
) : ExchangeOrderService {
    private val log = LoggerFactory.getLogger(ExchangeOrderServiceImpl::class.java)

    private val terminalStatuses = setOf(
        OrderStatus.SETTLED,
        OrderStatus.SUCCESS,
        OrderStatus.FAILED,
        OrderStatus.CANCELED,
        OrderStatus.COMPENSATED
    )

    @Transactional
    override fun requestBuyOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String?
    ): ExchangeOrderResponse {
        return createQueuedOrder(
            userId = userId,
            orderType = OrderType.BUY,
            fromCurrencyCode = "KRW",
            toCurrencyCode = currencyCode,
            fromAmount = fromAmount,
            idempotencyKey = idempotencyKey
        ).toDto()
    }

    @Transactional
    override fun requestSellOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String?
    ): ExchangeOrderResponse {
        return createQueuedOrder(
            userId = userId,
            orderType = OrderType.SELL,
            fromCurrencyCode = currencyCode,
            toCurrencyCode = "KRW",
            fromAmount = fromAmount,
            idempotencyKey = idempotencyKey
        ).toDto()
    }

    @Transactional
    override fun requestArbitrageOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String?
    ): ExchangeOrderResponse {
        return createQueuedOrder(
            userId = userId,
            orderType = OrderType.ARBITRAGE,
            fromCurrencyCode = "KRW",
            toCurrencyCode = currencyCode,
            fromAmount = fromAmount,
            idempotencyKey = idempotencyKey
        ).toDto()
    }

    @Transactional
    override fun processQueuedOrder(orderId: Long): ExchangeOrderResponse {
        val order = exchangeOrderRepository.findByIdForUpdate(orderId)
            ?: error("Exchange order not found: $orderId")

        if (order.status in terminalStatuses) {
            return order.toDto()
        }

        order.nextAttemptAt?.let { due ->
            if (due.isAfter(Instant.now())) throw OrderRetryNotDueException(due)
        }

        order.status = OrderStatus.PROCESSING
        order.processedAt = LocalDateTime.now()
        order.nextAttemptAt = null
        requestValidator.validateForProcessing(order)

        return when (order.orderType) {
            OrderType.BUY -> executeBuyOrder(order, isArbitrage = false)
            OrderType.SELL -> executeSellOrder(order, isArbitrage = false)
            OrderType.ARBITRAGE -> executeArbitrageParentOrder(order)
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
            return OrderRetryDecision(terminal = true)
        }

        val delay = Duration.ofSeconds(1L shl order.retryAttempts.coerceAtMost(6))
        order.nextAttemptAt = Instant.now().plus(delay).truncatedTo(ChronoUnit.MILLIS)
        order.status = OrderStatus.QUEUED
        return OrderRetryDecision(terminal = false, nextAttemptAt = order.nextAttemptAt)
    }

    @Transactional
    override fun buyOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        isArbitrage: Boolean
    ): ExchangeOrderResponse {
        val order = createProcessingOrder(
            userId = userId,
            orderType = OrderType.BUY,
            fromCurrencyCode = "KRW",
            toCurrencyCode = currencyCode,
            fromAmount = fromAmount
        )
        return executeBuyOrder(order, isArbitrage)
    }

    @Transactional
    override fun sellOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        isArbitrage: Boolean
    ): ExchangeOrderResponse {
        val order = createProcessingOrder(
            userId = userId,
            orderType = OrderType.SELL,
            fromCurrencyCode = currencyCode,
            toCurrencyCode = "KRW",
            fromAmount = fromAmount
        )
        return executeSellOrder(order, isArbitrage)
    }

    @Transactional
    override fun arbitrageOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        isArbitrage: Boolean
    ): Pair<ExchangeOrderResponse, ExchangeOrderResponse> {
        val buy = buyOrder(userId, currencyCode, fromAmount, true)
        val sell = sellOrder(userId, currencyCode, buy.toAmount, true)
        return buy to sell
    }

    private fun executeArbitrageParentOrder(order: ExchangeOrder): ExchangeOrderResponse {
        val currency = currencyRepository.findById(order.toCurrencyId)
            .orElseThrow { IllegalStateException("Currency not found: ${order.toCurrencyId}") }

        val (buy, sell) = arbitrageOrder(
            userId = order.userId,
            currencyCode = currency.code,
            fromAmount = order.fromAmount,
            isArbitrage = true
        )

        order.bankId = sell.bankId
        order.exchangeRate = sell.exchangeRate
        order.toAmount = sell.toAmount
        order.status = OrderStatus.SETTLED
        order.completedAt = LocalDateTime.now()

        log.info(
            "arbitrage parent order[{}] settled by buyOrder={}, sellOrder={}",
            order.id,
            buy.id,
            sell.id
        )

        return order.toDto()
    }

    private fun executeBuyOrder(order: ExchangeOrder, isArbitrage: Boolean): ExchangeOrderResponse {
        val fromCurrency = getCurrency(order.fromCurrencyId)
        val toCurrency = getCurrency(order.toCurrencyId)
        val bestRate = if (isArbitrage) {
            exchangeService.getBestBuyBaseRate(toCurrency.code)
        } else {
            exchangeService.getBestBuyRate(toCurrency.code)
        }

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

        val companyKRWAccount = findCompanyAccount(fromCurrency.id)
        val companyFXAccount = findCompanyAccount(toCurrency.id)
        val companyCommissionAccount = findCommissionAccount()

        walletService.userToCompany(order.userId, fromCurrency.id, companyKRWAccount.id!!, exchangeAmount, order.id)

        if (commissionAmount > BigDecimal.ZERO) {
            walletService.userToCompany(
                order.userId,
                fromCurrency.id,
                companyCommissionAccount.id!!,
                commissionAmount,
                order.id
            )
        }

        walletService.bankToCompany(companyKRWAccount.id!!, bestRate.bankId, exchangeAmount.negate())
        walletService.bankToCompany(companyFXAccount.id!!, bestRate.bankId, toAmount)
        walletService.companyToUser(companyFXAccount.id!!, order.userId, toCurrency.id, toAmount, order.id)

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

        return order.toDto()
    }

    private fun executeSellOrder(order: ExchangeOrder, isArbitrage: Boolean): ExchangeOrderResponse {
        val fromCurrency = getCurrency(order.fromCurrencyId)
        val toCurrency = getCurrency(order.toCurrencyId)
        val bestRate = if (isArbitrage) {
            exchangeService.getBestSellBaseRate(fromCurrency.code)
        } else {
            exchangeService.getBestSellRate(fromCurrency.code)
        }

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
            isArbitrage = isArbitrage
        )
        val toAmount = rawToAmount.minus(commissionAmount)

        order.toAmount = toAmount

        val companyFXAccount = findCompanyAccount(fromCurrency.id)
        val companyKRWAccount = findCompanyAccount(toCurrency.id)
        val companyCommissionAccount = findCommissionAccount()

        walletService.userToCompany(order.userId, fromCurrency.id, companyFXAccount.id!!, order.fromAmount, order.id)
        walletService.bankToCompany(companyFXAccount.id!!, bestRate.bankId, order.fromAmount.negate())
        walletService.bankToCompany(companyKRWAccount.id!!, bestRate.bankId, rawToAmount)

        if (commissionAmount > BigDecimal.ZERO) {
            walletService.companyToCompany(companyKRWAccount.id!!, companyCommissionAccount.id!!, commissionAmount)
        }

        walletService.companyToUser(companyKRWAccount.id!!, order.userId, toCurrency.id, toAmount, order.id)

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

        return order.toDto()
    }

    private fun createQueuedOrder(
        userId: Long,
        orderType: OrderType,
        fromCurrencyCode: String,
        toCurrencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String?
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

        val usdEquivalentAmount = requestValidator.validateAfterUserLock(
            userId = userId,
            orderType = orderType,
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            fromAmount = fromAmount
        )

        return saveOrder(
            userId = userId,
            orderType = orderType,
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            fromAmount = fromAmount,
            status = OrderStatus.QUEUED,
            idempotencyKey = idempotencyKey,
            usdEquivalentAmount = usdEquivalentAmount
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
            idempotencyKey = null,
            usdEquivalentAmount = null
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
        usdEquivalentAmount: BigDecimal?
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
                orderType = orderType,
                status = status,
                idempotencyKey = idempotencyKey,
                usdEquivalentAmount = usdEquivalentAmount
            )
        )

        if (status == OrderStatus.QUEUED) {
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

    private fun findCompanyAccount(currencyId: Long): InternalAccount {
        val meta = CompanyAccount.ofCurrencyId(currencyId)
        return internalAccountRepository.findByCode(meta)
            ?.takeIf { it.currencyId == currencyId }
            ?: error("Company account not found: currencyId=$currencyId")
    }

    private fun findCommissionAccount(): InternalAccount {
        val meta = CompanyAccount.COMMISSION_ACCOUNT
        return internalAccountRepository.findByCode(meta)
            ?.takeIf { it.currencyId == meta.currencyId }
            ?: error("Company commission account not found")
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
