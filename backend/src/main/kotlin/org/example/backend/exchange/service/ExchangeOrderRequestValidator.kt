package org.example.backend.exchange.service

import org.example.backend.enums.OrderType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.exchange.repository.ExchangeReservationRepository
import org.example.backend.finance.domain.Currency
import org.example.backend.user.repository.UserRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class ExchangeOrderRequestValidator(
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
    private val reservationRepository: ExchangeReservationRepository,
    private val exchangeService: ExchangeService
) {
    fun validate(
        userId: Long,
        orderType: OrderType,
        fromCurrency: Currency,
        toCurrency: Currency,
        fromAmount: BigDecimal
    ) {
        if (fromAmount <= BigDecimal.ZERO) {
            throw UserException(ErrorCode.INVALID_EXCHANGE_AMOUNT)
        }
        lockUser(userId)

        validateAfterUserLock(userId, orderType, fromCurrency, toCurrency, fromAmount)
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
    ) {
        if (fromAmount <= BigDecimal.ZERO) {
            throw UserException(ErrorCode.INVALID_EXCHANGE_AMOUNT)
        }
        if (fromAmount.stripTrailingZeros().scale() > fromCurrency.scale) {
            throw UserException(ErrorCode.INVALID_EXCHANGE_AMOUNT)
        }

        validateAvailableBalance(userId, fromCurrency.id, fromAmount)

        val minimumAmount = when (orderType) {
            OrderType.BUY -> exchangeService.getBestBuyRate(toCurrency.code).bestRate
            OrderType.ARBITRAGE -> exchangeService.getBestBuyBaseRate(toCurrency.code).bestRate
            OrderType.SELL -> fromCurrency.unit
        }
        if (fromAmount < minimumAmount) {
            throw UserException(ErrorCode.INVALID_EXCHANGE_AMOUNT)
        }

        if (orderType == OrderType.ARBITRAGE && fromAmount.compareTo(ArbitrageQuotePolicy.AMOUNT_KRW) != 0) {
            throw UserException(ErrorCode.ARBITRAGE_AMOUNT_EXCEEDED)
        }
    }

    fun lockUserForProcessing(userId: Long) {
        userRepository.findByIdForUpdate(userId)
            ?: throw UserException(ErrorCode.USER_NOT_FOUND)
    }

    private fun validateAvailableBalance(userId: Long, currencyId: Long, fromAmount: BigDecimal) {
        val wallet = walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)
        val reservedAmount = reservationRepository.sumActiveFunds(userId, currencyId)
        val availableAmount = wallet.balance.subtract(reservedAmount)

        if (availableAmount < fromAmount) {
            throw UserException(
                ErrorCode.INSUFFICIENT_WALLET_BALANCE,
                "insufficient available balance: available=$availableAmount, requested=$fromAmount"
            )
        }
    }

}
