package org.example.backend.exchange.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.example.backend.enums.ExchangeType
import org.example.backend.enums.OrderType
import org.example.backend.enums.UserType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.exchange.dto.BestExchangeRateDto
import org.example.backend.exchange.repository.ExchangeReservationRepository
import org.example.backend.finance.domain.Currency
import org.example.backend.user.domain.User
import org.example.backend.user.domain.Wallet
import org.example.backend.user.repository.UserRepository
import org.example.backend.user.repository.WalletRepository
import java.math.BigDecimal
import java.time.LocalDateTime

class ExchangeOrderRequestValidatorTest : FunSpec({
    val userRepository = mockk<UserRepository>()
    val walletRepository = mockk<WalletRepository>()
    val reservationRepository = mockk<ExchangeReservationRepository>()
    val exchangeService = mockk<ExchangeService>()

    val validator = ExchangeOrderRequestValidator(
        userRepository = userRepository,
        walletRepository = walletRepository,
        reservationRepository = reservationRepository,
        exchangeService = exchangeService
    )

    val userId = 10L
    val krw = Currency(id = 1L, code = "KRW", name = "Korean Won", scale = 0, unit = BigDecimal.ONE)
    val usd = Currency(id = 2L, code = "USD", name = "US Dollar", scale = 2, unit = BigDecimal.ONE)
    val jpy = Currency(id = 3L, code = "JPY", name = "Japanese Yen", scale = 0, unit = BigDecimal("100"))
    val user = User(
        id = userId,
        device = "device",
        password = "password",
        name = "name",
        phone = "01000000000",
        type = UserType.GENERAL,
        createdAt = LocalDateTime.now()
    )

    beforeTest {
        clearAllMocks()
        every { userRepository.findByIdForUpdate(userId) } returns user
        every { exchangeService.getBestBuyBaseRate("USD") } returns BestExchangeRateDto(
            bankId = 1L,
            currencyId = usd.id,
            bestRate = BigDecimal("1300"),
            type = ExchangeType.BUY
        )
        every { exchangeService.getBestBuyRate("USD") } returns BestExchangeRateDto(
            bankId = 1L,
            currencyId = usd.id,
            bestRate = BigDecimal("1300"),
            type = ExchangeType.BUY
        )
    }

    test("rejects an order when active orders already reserve the available balance") {
        every { walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, krw.id) } returns Wallet(
            id = 1L,
            userId = userId,
            currencyId = krw.id,
            balance = BigDecimal("100000")
        )
        every {
            reservationRepository.sumActiveFunds(userId, krw.id, any())
        } returns BigDecimal("80000")

        val exception = shouldThrow<UserException> {
            validator.validate(
                userId = userId,
                orderType = OrderType.BUY,
                fromCurrency = krw,
                toCurrency = usd,
                fromAmount = BigDecimal("30000")
            )
        }

        exception.errorCode shouldBe ErrorCode.INSUFFICIENT_WALLET_BALANCE.code
    }

    test("normal buy has no product daily USD quota") {
        every { walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, krw.id) } returns Wallet(
            id = 1L,
            userId = userId,
            currencyId = krw.id,
            balance = BigDecimal("10000000")
        )
        every {
            reservationRepository.sumActiveFunds(userId, krw.id, any())
        } returns BigDecimal.ZERO
        validator.validate(userId, OrderType.BUY, krw, usd, BigDecimal("5200000"))
    }

    test("arbitrage accepts only the fixed one million KRW amount") {
        every { walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, krw.id) } returns Wallet(
            id = 1L,
            userId = userId,
            currencyId = krw.id,
            balance = BigDecimal("10000000")
        )
        every {
            reservationRepository.sumActiveFunds(userId, krw.id, any())
        } returns BigDecimal.ZERO
        validator.validate(userId, OrderType.ARBITRAGE, krw, usd, BigDecimal("1000000"))
        shouldThrow<UserException> {
            validator.validate(userId, OrderType.ARBITRAGE, krw, usd, BigDecimal("1000001"))
        }.errorCode shouldBe ErrorCode.ARBITRAGE_AMOUNT_EXCEEDED.code
        shouldThrow<UserException> {
            validator.validate(userId, OrderType.ARBITRAGE, krw, usd, BigDecimal("999999"))
        }.errorCode shouldBe ErrorCode.ARBITRAGE_AMOUNT_EXCEEDED.code
    }

    test("JPY arbitrage validation does not require a USD quote") {
        every { walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, krw.id) } returns Wallet(
            id = 1L, userId = userId, currencyId = krw.id, balance = BigDecimal("1000000")
        )
        every { reservationRepository.sumActiveFunds(userId, krw.id, any()) } returns BigDecimal.ZERO
        every { exchangeService.getBestBuyBaseRate("JPY") } returns BestExchangeRateDto(
            bankId = 1L, currencyId = jpy.id, bestRate = BigDecimal("900"), type = ExchangeType.BUY
        )

        validator.validate(userId, OrderType.ARBITRAGE, krw, jpy, BigDecimal("1000000"))
        verify(exactly = 0) { exchangeService.getBestBuyBaseRate("USD") }
    }

    test("rejects a buy below the quoted unit price before accepting the order") {
        every { walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, krw.id) } returns Wallet(
            id = 1L, userId = userId, currencyId = krw.id, balance = BigDecimal("10000")
        )
        every { reservationRepository.sumActiveFunds(userId, krw.id, any()) } returns BigDecimal.ZERO

        val exception = shouldThrow<UserException> {
            validator.validate(userId, OrderType.BUY, krw, usd, BigDecimal("1299"))
        }

        exception.errorCode shouldBe ErrorCode.INVALID_EXCHANGE_AMOUNT.code
    }

    test("rejects a JPY sale below its 100-unit quotation before accepting the order") {
        every { walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, jpy.id) } returns Wallet(
            id = 2L, userId = userId, currencyId = jpy.id, balance = BigDecimal("1000")
        )
        every { reservationRepository.sumActiveFunds(userId, jpy.id, any()) } returns BigDecimal.ZERO

        val exception = shouldThrow<UserException> {
            validator.validate(userId, OrderType.SELL, jpy, krw, BigDecimal("99"))
        }

        exception.errorCode shouldBe ErrorCode.INVALID_EXCHANGE_AMOUNT.code
    }
})
