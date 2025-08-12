package org.example.backend.exchange.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.example.backend.enums.OrderStatus
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.dto.ExchangeDTO
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.service.WalletService
import java.math.BigDecimal

class ExchangeOrderServiceImplTest : BehaviorSpec({

    // Mock 의존성 생성
    val exchangeOrderRepository = mockk<ExchangeOrderRepository>()
    val exchangeService = mockk<ExchangeService>()
    val transactionService = mockk<TransactionService>()
    val ledgerService = mockk<ExchangeLedgerService>()
    val currencyRepository = mockk<CurrencyRepository>()
    val walletService = mockk<WalletService>()
    val accountRepository = mockk<AccountRepository>()

    val service = ExchangeOrderServiceImpl(
        exchangeOrderRepository,
        exchangeService,
        transactionService,
        ledgerService,
        currencyRepository,
        walletService,
        accountRepository
    )

    Given("buyOrder 호출") {
        val userId = 1L
        val currencyCode = "USD"
        val fromAmount = BigDecimal(1000)

        val krwCurrency = Currency(id = 1L, code = "KRW", name = "대한민국 원", scale = 0, unit = BigDecimal.ONE)
        val usdCurrency = Currency(id = 2L, code = "USD", name = "미국 달러", scale = 2, unit = BigDecimal.ONE,)

        val bestRate = ExchangeDTO(bankId = 1L, currencyId = 2L, bestRate = BigDecimal("1300.00"), type = org.example.backend.enums.ExchangeType.BUY)

        val savedOrder = ExchangeOrder(
            id = 100L,
            userId = userId,
            bankId = bestRate.bankId,
            fromCurrencyId = krwCurrency.id,
            toCurrencyId = usdCurrency.id,
            fromAmount = fromAmount,
            toAmount = BigDecimal.ZERO,
            exchangeRate = bestRate.bestRate,
            status = OrderStatus.PENDING
        )

        // Mock 동작 정의
        every { currencyRepository.findByCode("KRW") } returns krwCurrency
        every { currencyRepository.findByCode(currencyCode) } returns usdCurrency
        every { exchangeService.getBestBuyRate(currencyCode) } returns bestRate
        every { exchangeOrderRepository.save(any()) } returns savedOrder
        every { exchangeService.calculateExchange(any(), any(), any(), any()) } returns (BigDecimal(1) to BigDecimal.ZERO)

        every { accountRepository.findByCurrencyIdAndAccountNum(any(), any()) } returns mockk {
            every { id } returns 10L
        }

        // 금액 이동
        every { walletService.userToCompany(any(), any(), any(), any()) } returns BigDecimal.ZERO
        every { walletService.bankToCompany(any(), any(), any()) } returns BigDecimal.ZERO
        every { walletService.companyToUser(any(), any(), any(), any()) } returns BigDecimal.ZERO

        // 거래 기록
        every { transactionService.record(any()) } returns mockk()
        every { ledgerService.record(any()) } returns mockk()

        When("정상 호출하면") {
            val result = service.buyOrder(userId, currencyCode, fromAmount, false)

            Then("상태가 SUCCESS여야 한다") {
                result.status shouldBe OrderStatus.SUCCESS
                result.userId shouldBe userId
                result.bankId shouldBe bestRate.bankId
            }
        }
    }
})
