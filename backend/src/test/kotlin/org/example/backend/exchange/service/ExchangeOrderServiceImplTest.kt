package org.example.backend.exchange.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.example.backend.enums.ExchangeType
import org.example.backend.enums.CompanyAccount
import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.enums.PublicOrderStatus
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.dto.BestExchangeRateDto
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.exchange.repository.OutboxEventRepository
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.domain.InternalAccount
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.finance.repository.InternalAccountRepository
import org.example.backend.user.service.WalletService
import java.math.BigDecimal
import java.util.Optional

class ExchangeOrderServiceImplTest : FunSpec({
    val exchangeOrderRepository = mockk<ExchangeOrderRepository>()
    val exchangeService = mockk<ExchangeService>()
    val transactionService = mockk<TransactionService>(relaxed = true)
    val ledgerService = mockk<ExchangeLedgerService>(relaxed = true)
    val currencyRepository = mockk<CurrencyRepository>()
    val walletService = mockk<WalletService>()
    val internalAccountRepository = mockk<InternalAccountRepository>()
    val requestValidator = mockk<ExchangeOrderRequestValidator>()
    val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)

    val sut = ExchangeOrderServiceImpl(
        exchangeOrderRepository = exchangeOrderRepository,
        exchangeService = exchangeService,
        transactionService = transactionService,
        ledgerService = ledgerService,
        currencyRepository = currencyRepository,
        walletService = walletService,
        internalAccountRepository = internalAccountRepository,
        requestValidator = requestValidator,
        outboxEventRepository = outboxEventRepository
    )

    val krw = Currency(id = 1L, code = "KRW", name = "Korean Won", scale = 0, unit = BigDecimal.ONE)
    val usd = Currency(id = 2L, code = "USD", name = "US Dollar", scale = 2, unit = BigDecimal.ONE)

    beforeTest {
        clearAllMocks()
    }

    test("buyOrder settles a valid buy order and records transaction/ledger") {
        val userId = 10L
        val currencyCode = "USD"
        val fromAmount = BigDecimal("100000")
        val bestBuy = BestExchangeRateDto(
            bankId = 1L,
            currencyId = usd.id,
            bestRate = BigDecimal("1300.00"),
            ExchangeType.BUY
        )
        val savedOrder = ExchangeOrder(
            id = 11L,
            userId = userId,
            bankId = 0L,
            fromCurrencyId = krw.id,
            toCurrencyId = usd.id,
            fromAmount = fromAmount,
            toAmount = BigDecimal.ZERO,
            exchangeRate = BigDecimal.ZERO,
            orderType = OrderType.BUY,
            status = OrderStatus.PROCESSING
        )
        val companyKrwAccount = InternalAccount(
            id = 101L,
            code = CompanyAccount.KRW_ACCOUNT,
            currencyId = krw.id,
        )
        val companyUsdAccount = InternalAccount(
            id = 102L,
            code = CompanyAccount.USD_ACCOUNT,
            currencyId = usd.id,
        )
        val commissionAccount = InternalAccount(
            id = 103L,
            code = CompanyAccount.COMMISSION_ACCOUNT,
            currencyId = krw.id,
        )

        every { currencyRepository.findByCode("KRW") } returns krw
        every { currencyRepository.findByCode(currencyCode) } returns usd
        every { currencyRepository.findById(krw.id) } returns Optional.of(krw)
        every { currencyRepository.findById(usd.id) } returns Optional.of(usd)
        every { exchangeOrderRepository.save(any()) } returns savedOrder
        every { exchangeService.getBestBuyRate(currencyCode) } returns bestBuy
        every {
            exchangeService.calculateExchange(krw, usd, bestBuy.bestRate, BigDecimal("99997"))
        } returns (BigDecimal("76.92") to BigDecimal.ZERO)
        every {
            internalAccountRepository.findByCode(CompanyAccount.KRW_ACCOUNT)
        } returns companyKrwAccount
        every {
            internalAccountRepository.findByCode(CompanyAccount.USD_ACCOUNT)
        } returns companyUsdAccount
        every {
            internalAccountRepository.findByCode(CompanyAccount.COMMISSION_ACCOUNT)
        } returns commissionAccount
        every { walletService.userToCompany(any(), any(), any(), any(), any()) } returns BigDecimal.ZERO
        every { walletService.bankToCompany(any(), any(), any()) } returns BigDecimal.ZERO
        every { walletService.companyToUser(any(), any(), any(), any(), any()) } returns BigDecimal.ZERO

        val res = sut.buyOrder(userId, currencyCode, fromAmount, false)

        res.status shouldBe PublicOrderStatus.COMPLETED
        res.toAmount shouldBe BigDecimal("76.92")
        verify(exactly = 2) { transactionService.record(any()) }
        verify(exactly = 1) { ledgerService.record(any()) }
    }

    test("buyOrder rejects an amount below the minimum buy amount") {
        val userId = 99L
        val currencyCode = "USD"
        val bestBuy = BestExchangeRateDto(
            bankId = 1L,
            currencyId = usd.id,
            bestRate = BigDecimal("1300.00"),
            ExchangeType.BUY
        )
        val savedOrder = ExchangeOrder(
            id = 12L,
            userId = userId,
            bankId = 0L,
            fromCurrencyId = krw.id,
            toCurrencyId = usd.id,
            fromAmount = BigDecimal("1000"),
            toAmount = BigDecimal.ZERO,
            exchangeRate = BigDecimal.ZERO,
            orderType = OrderType.BUY,
            status = OrderStatus.PROCESSING
        )

        every { currencyRepository.findByCode("KRW") } returns krw
        every { currencyRepository.findByCode(currencyCode) } returns usd
        every { currencyRepository.findById(krw.id) } returns Optional.of(krw)
        every { currencyRepository.findById(usd.id) } returns Optional.of(usd)
        every { exchangeOrderRepository.save(any()) } returns savedOrder
        every { exchangeService.getBestBuyRate(currencyCode) } returns bestBuy

        shouldThrow<IllegalArgumentException> {
            sut.buyOrder(userId, currencyCode, BigDecimal("1000"), false)
        }
    }

    test("sellOrder rejects an amount below the source currency quotation unit") {
        val jpy = Currency(id = 3L, code = "JPY", name = "Japanese Yen", scale = 0, unit = BigDecimal("100"))
        val savedOrder = ExchangeOrder(
            id = 13L,
            userId = 99L,
            bankId = 0L,
            fromCurrencyId = jpy.id,
            toCurrencyId = krw.id,
            fromAmount = BigDecimal("99"),
            toAmount = BigDecimal.ZERO,
            exchangeRate = BigDecimal.ZERO,
            orderType = OrderType.SELL,
            status = OrderStatus.PROCESSING
        )
        every { currencyRepository.findByCode("JPY") } returns jpy
        every { currencyRepository.findByCode("KRW") } returns krw
        every { currencyRepository.findById(jpy.id) } returns Optional.of(jpy)
        every { currencyRepository.findById(krw.id) } returns Optional.of(krw)
        every { exchangeOrderRepository.save(any()) } returns savedOrder
        every { exchangeService.getBestSellRate("JPY") } returns BestExchangeRateDto(
            bankId = 1L,
            currencyId = jpy.id,
            bestRate = BigDecimal("930"),
            type = ExchangeType.SELL
        )

        shouldThrow<IllegalArgumentException> {
            sut.sellOrder(99L, "JPY", BigDecimal("99"), false)
        }
        verify(exactly = 0) { walletService.userToCompany(any(), any(), any(), any(), any()) }
    }

    test("requestBuyOrder returns the existing order for the same idempotency key and payload") {
        val existingOrder = ExchangeOrder(
            id = 20L,
            userId = 10L,
            bankId = 0L,
            fromCurrencyId = krw.id,
            toCurrencyId = usd.id,
            fromAmount = BigDecimal("100000"),
            toAmount = BigDecimal.ZERO,
            exchangeRate = BigDecimal.ZERO,
            orderType = OrderType.BUY,
            status = OrderStatus.QUEUED,
            idempotencyKey = "same-key"
        )

        every { currencyRepository.findByCode("KRW") } returns krw
        every { currencyRepository.findByCode("USD") } returns usd
        every { requestValidator.lockUser(10L) } returns Unit
        every { exchangeOrderRepository.findByUserIdAndIdempotencyKey(10L, "same-key") } returns existingOrder

        val response = sut.requestBuyOrder(10L, "USD", BigDecimal("100000.00"), "same-key")

        response.id shouldBe 20L
        verify(exactly = 0) { exchangeOrderRepository.save(any()) }
        verify(exactly = 0) { requestValidator.validateAfterUserLock(any(), any(), any(), any(), any()) }
    }

    test("requestBuyOrder rejects a reused idempotency key with a different payload") {
        val existingOrder = ExchangeOrder(
            id = 21L,
            userId = 10L,
            bankId = 0L,
            fromCurrencyId = krw.id,
            toCurrencyId = usd.id,
            fromAmount = BigDecimal("100000"),
            toAmount = BigDecimal.ZERO,
            exchangeRate = BigDecimal.ZERO,
            orderType = OrderType.BUY,
            status = OrderStatus.QUEUED,
            idempotencyKey = "reused-key"
        )

        every { currencyRepository.findByCode("KRW") } returns krw
        every { currencyRepository.findByCode("USD") } returns usd
        every { requestValidator.lockUser(10L) } returns Unit
        every { exchangeOrderRepository.findByUserIdAndIdempotencyKey(10L, "reused-key") } returns existingOrder

        val exception = shouldThrow<UserException> {
            sut.requestBuyOrder(10L, "USD", BigDecimal("200000"), "reused-key")
        }

        exception.errorCode shouldBe ErrorCode.IDEMPOTENCY_KEY_CONFLICT.code
        verify(exactly = 0) { exchangeOrderRepository.save(any()) }
    }

    test("markFailed does not overwrite an already settled order") {
        val settledOrder = ExchangeOrder(
            id = 30L,
            userId = 10L,
            bankId = 1L,
            fromCurrencyId = krw.id,
            toCurrencyId = usd.id,
            fromAmount = BigDecimal("100000"),
            toAmount = BigDecimal("76.92"),
            exchangeRate = BigDecimal("1300"),
            orderType = OrderType.BUY,
            status = OrderStatus.SETTLED
        )
        every { exchangeOrderRepository.findByIdForUpdate(30L) } returns settledOrder

        sut.recordProcessingFailure(30L, "lease-30", "lease expired", 3).terminal shouldBe true

        settledOrder.status shouldBe OrderStatus.SETTLED
        settledOrder.failureReason shouldBe null
    }
})
