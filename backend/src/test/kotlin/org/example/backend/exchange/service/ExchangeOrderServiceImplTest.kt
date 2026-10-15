package org.example.backend.exchange.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.exchange.repository.ExchangeReservationRepository
import org.example.backend.exchange.repository.OutboxEventRepository
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.CurrencyRepository
import java.math.BigDecimal

class ExchangeOrderServiceImplTest : FunSpec({
    val exchangeOrderRepository = mockk<ExchangeOrderRepository>()
    val currencyRepository = mockk<CurrencyRepository>()
    val requestValidator = mockk<ExchangeOrderRequestValidator>()
    val sut = ExchangeOrderServiceImpl(
        exchangeOrderRepository = exchangeOrderRepository,
        exchangeService = mockk(),
        transactionService = mockk(),
        ledgerService = mockk(),
        currencyRepository = currencyRepository,
        postingService = mockk(),
        requestValidator = requestValidator,
        outboxEventRepository = mockk<OutboxEventRepository>(),
        reservationRepository = mockk<ExchangeReservationRepository>(),
        operations = mockk()
    )
    val krw = Currency(id = 1L, code = "KRW", name = "Korean Won", scale = 0, unit = BigDecimal.ONE)
    val usd = Currency(id = 2L, code = "USD", name = "US Dollar", scale = 2, unit = BigDecimal.ONE)

    beforeTest { clearAllMocks() }

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

    test("failure report does not overwrite an already settled order") {
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
