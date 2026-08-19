package org.example.backend.exchange.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.example.backend.enums.ExchangeType
import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.enums.PublicOrderStatus
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.dto.BestExchangeRateDto
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.user.domain.Account
import org.example.backend.user.repository.AccountRepository
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
    val accountRepository = mockk<AccountRepository>()
    val requestValidator = mockk<ExchangeOrderRequestValidator>()

    val sut = ExchangeOrderServiceImpl(
        exchangeOrderRepository = exchangeOrderRepository,
        exchangeService = exchangeService,
        transactionService = transactionService,
        ledgerService = ledgerService,
        currencyRepository = currencyRepository,
        walletService = walletService,
        accountRepository = accountRepository,
        requestValidator = requestValidator
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
        val companyKrwAccount = Account(
            id = 101L,
            userId = 1L,
            bankId = 1L,
            currencyId = krw.id,
            accountNum = "222222-22-222222"
        )
        val companyUsdAccount = Account(
            id = 102L,
            userId = 1L,
            bankId = 4L,
            currencyId = usd.id,
            accountNum = "444444-44-444444"
        )
        val commissionAccount = Account(
            id = 103L,
            userId = 1L,
            bankId = 1L,
            currencyId = krw.id,
            accountNum = "111111-11-111111"
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
            accountRepository.findByCurrencyIdAndAccountNum(krw.id, "222222-22-222222")
        } returns companyKrwAccount
        every {
            accountRepository.findByCurrencyIdAndAccountNum(usd.id, "444444-44-444444")
        } returns companyUsdAccount
        every {
            accountRepository.findByCurrencyIdAndAccountNum(krw.id, "111111-11-111111")
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
})
