package org.example.backend.exchange.service

import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.user.repository.UserRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

@Component
class ExchangeOrderRequestValidator(
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
    private val exchangeOrderRepository: ExchangeOrderRepository,
    private val currencyRepository: CurrencyRepository,
    private val exchangeService: ExchangeService
) {
    fun validate(
        userId: Long,
        orderType: OrderType,
        fromCurrency: Currency,
        toCurrency: Currency,
        fromAmount: BigDecimal
    ): BigDecimal {
        if (fromAmount <= BigDecimal.ZERO) {
            throw UserException(ErrorCode.INVALID_EXCHANGE_AMOUNT)
        }

        lockUser(userId)

        return validateAfterUserLock(userId, orderType, fromCurrency, toCurrency, fromAmount)
    }

    fun lockUser(userId: Long) {
        userRepository.findByIdForUpdate(userId)
            ?: throw UserException(ErrorCode.USER_NOT_FOUND)
    }

    fun validateAfterUserLock(
        userId: Long,
        orderType: OrderType,
        fromCurrency: Currency,
        toCurrency: Currency,
        fromAmount: BigDecimal
    ): BigDecimal {
        if (fromAmount <= BigDecimal.ZERO) {
            throw UserException(ErrorCode.INVALID_EXCHANGE_AMOUNT)
        }

        validateAvailableBalance(userId, fromCurrency.id, fromAmount)

        val usdEquivalentAmount = calculateUsdEquivalentAmount(
            orderType = orderType,
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            fromAmount = fromAmount
        )
        validateDailyLimit(userId, usdEquivalentAmount)

        return usdEquivalentAmount
    }

    fun validateForProcessing(order: ExchangeOrder) {
        userRepository.findByIdForUpdate(order.userId)
            ?: throw UserException(ErrorCode.USER_NOT_FOUND)

        val orderDate = order.requestedAt?.toLocalDate() ?: LocalDate.now(SEOUL_ZONE)
        val usedUsdAmount = exchangeOrderRepository.sumDailyUsdEquivalentAmount(
            userId = order.userId,
            startAt = orderDate.atStartOfDay(),
            endAt = orderDate.plusDays(1).atStartOfDay(),
            statuses = DAILY_LIMIT_STATUSES
        )

        if (usedUsdAmount > DAILY_LIMIT_USD) {
            throw UserException(ErrorCode.DAILY_EXCHANGE_LIMIT_EXCEEDED)
        }
    }

    private fun validateAvailableBalance(userId: Long, currencyId: Long, fromAmount: BigDecimal) {
        val wallet = walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)
        val reservedAmount = exchangeOrderRepository.sumReservedFromAmount(
            userId = userId,
            currencyId = currencyId,
            statuses = RESERVING_STATUSES
        )
        val availableAmount = wallet.balance.subtract(reservedAmount)

        if (availableAmount < fromAmount) {
            throw UserException(
                ErrorCode.INSUFFICIENT_WALLET_BALANCE,
                "insufficient available balance: available=$availableAmount, requested=$fromAmount"
            )
        }
    }

    private fun calculateUsdEquivalentAmount(
        orderType: OrderType,
        fromCurrency: Currency,
        toCurrency: Currency,
        fromAmount: BigDecimal
    ): BigDecimal {
        if (fromCurrency.code.equals(USD, ignoreCase = true)) {
            return fromAmount.setScale(USD_SCALE, RoundingMode.UP)
        }

        val krwAmount = when {
            fromCurrency.code.equals(KRW, ignoreCase = true) -> fromAmount
            toCurrency.code.equals(KRW, ignoreCase = true) || orderType == OrderType.SELL -> {
                val sourceRate = exchangeService.getBestSellBaseRate(fromCurrency.code)
                val ratePerOne = sourceRate.bestRate.divide(fromCurrency.unit, RATE_SCALE, RoundingMode.HALF_UP)
                fromAmount.multiply(ratePerOne)
            }
            else -> error("Unsupported exchange direction: ${fromCurrency.code} -> ${toCurrency.code}")
        }

        val usd = currencyRepository.findByCode(USD)
        val usdRate = exchangeService.getBestBuyBaseRate(USD)
        val usdRatePerOne = usdRate.bestRate.divide(usd.unit, RATE_SCALE, RoundingMode.HALF_UP)

        return krwAmount.divide(usdRatePerOne, USD_SCALE, RoundingMode.UP)
    }

    private fun validateDailyLimit(userId: Long, requestedUsdAmount: BigDecimal) {
        val today = LocalDate.now(SEOUL_ZONE)
        val usedUsdAmount = exchangeOrderRepository.sumDailyUsdEquivalentAmount(
            userId = userId,
            startAt = today.atStartOfDay(),
            endAt = today.plusDays(1).atStartOfDay(),
            statuses = DAILY_LIMIT_STATUSES
        )

        if (usedUsdAmount.add(requestedUsdAmount) > DAILY_LIMIT_USD) {
            throw UserException(
                ErrorCode.DAILY_EXCHANGE_LIMIT_EXCEEDED,
                "daily exchange limit exceeded: limit=$DAILY_LIMIT_USD USD, used=$usedUsdAmount USD, requested=$requestedUsdAmount USD"
            )
        }
    }

    companion object {
        val DAILY_LIMIT_USD: BigDecimal = BigDecimal("4000.000000")

        private const val KRW = "KRW"
        private const val USD = "USD"
        private const val RATE_SCALE = 10
        private const val USD_SCALE = 6
        private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private val RESERVING_STATUSES = listOf(
            OrderStatus.REQUESTED,
            OrderStatus.QUEUED,
            OrderStatus.PROCESSING,
            OrderStatus.PENDING
        )
        private val DAILY_LIMIT_STATUSES = listOf(
            OrderStatus.REQUESTED,
            OrderStatus.QUEUED,
            OrderStatus.PROCESSING,
            OrderStatus.SETTLED,
            OrderStatus.PENDING,
            OrderStatus.SUCCESS
        )
    }
}
