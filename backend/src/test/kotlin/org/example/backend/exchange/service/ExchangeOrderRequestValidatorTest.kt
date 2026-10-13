package org.example.backend.exchange.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.example.backend.enums.ExchangeType
import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.enums.UserType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.exchange.dto.BestExchangeRateDto
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.user.domain.User
import org.example.backend.user.domain.Wallet
import org.example.backend.user.repository.UserRepository
import org.example.backend.user.repository.WalletRepository
import java.math.BigDecimal
import java.time.LocalDateTime

class ExchangeOrderRequestValidatorTest : FunSpec({
    val userRepository = mockk<UserRepository>()
    val walletRepository = mockk<WalletRepository>()
    val exchangeOrderRepository = mockk<ExchangeOrderRepository>()
    val currencyRepository = mockk<CurrencyRepository>()
    val exchangeService = mockk<ExchangeService>()

    val validator = ExchangeOrderRequestValidator(
        userRepository = userRepository,
        walletRepository = walletRepository,
        exchangeOrderRepository = exchangeOrderRepository,
        currencyRepository = currencyRepository,
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
        every { currencyRepository.findByCode("USD") } returns usd
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
            exchangeOrderRepository.sumReservedFromAmount(userId, krw.id, any<Collection<OrderStatus>>())
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

    test("rejects an order that would exceed the daily 4000 USD limit") {
        every { walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, krw.id) } returns Wallet(
            id = 1L,
            userId = userId,
            currencyId = krw.id,
            balance = BigDecimal("10000000")
        )
        every {
            exchangeOrderRepository.sumReservedFromAmount(userId, krw.id, any<Collection<OrderStatus>>())
        } returns BigDecimal.ZERO
        every {
            exchangeOrderRepository.sumDailyUsdEquivalentAmount(
                userId,
                any(),
                any(),
                any<Collection<OrderStatus>>()
            )
        } returns BigDecimal("3999.000000")

        val exception = shouldThrow<UserException> {
            validator.validate(
                userId = userId,
                orderType = OrderType.BUY,
                fromCurrency = krw,
                toCurrency = usd,
                fromAmount = BigDecimal("2600")
            )
        }

        exception.errorCode shouldBe ErrorCode.DAILY_EXCHANGE_LIMIT_EXCEEDED.code
    }

    test("accepts an order when the daily total is exactly 4000 USD") {
        every { walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, krw.id) } returns Wallet(
            id = 1L,
            userId = userId,
            currencyId = krw.id,
            balance = BigDecimal("10000000")
        )
        every {
            exchangeOrderRepository.sumReservedFromAmount(userId, krw.id, any<Collection<OrderStatus>>())
        } returns BigDecimal.ZERO
        every {
            exchangeOrderRepository.sumDailyUsdEquivalentAmount(
                userId,
                any(),
                any(),
                any<Collection<OrderStatus>>()
            )
        } returns BigDecimal("3999.000000")

        val usdEquivalentAmount = validator.validate(
            userId = userId,
            orderType = OrderType.BUY,
            fromCurrency = krw,
            toCurrency = usd,
            fromAmount = BigDecimal("1300")
        )

        usdEquivalentAmount shouldBe BigDecimal("1.000000")
    }

    test("rejects a buy below the quoted unit price before accepting the order") {
        every { walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, krw.id) } returns Wallet(
            id = 1L, userId = userId, currencyId = krw.id, balance = BigDecimal("10000")
        )
        every { exchangeOrderRepository.sumReservedFromAmount(userId, krw.id, any<Collection<OrderStatus>>()) } returns BigDecimal.ZERO

        val exception = shouldThrow<UserException> {
            validator.validate(userId, OrderType.BUY, krw, usd, BigDecimal("1299"))
        }

        exception.errorCode shouldBe ErrorCode.INVALID_EXCHANGE_AMOUNT.code
    }

    test("rejects a JPY sale below its 100-unit quotation before accepting the order") {
        every { walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, jpy.id) } returns Wallet(
            id = 2L, userId = userId, currencyId = jpy.id, balance = BigDecimal("1000")
        )
        every { exchangeOrderRepository.sumReservedFromAmount(userId, jpy.id, any<Collection<OrderStatus>>()) } returns BigDecimal.ZERO

        val exception = shouldThrow<UserException> {
            validator.validate(userId, OrderType.SELL, jpy, krw, BigDecimal("99"))
        }

        exception.errorCode shouldBe ErrorCode.INVALID_EXCHANGE_AMOUNT.code
    }
})
