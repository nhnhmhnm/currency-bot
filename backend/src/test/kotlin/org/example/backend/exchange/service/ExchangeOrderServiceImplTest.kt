package org.example.backend.exchange.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.example.backend.enums.ExchangeType
import org.example.backend.enums.OrderStatus
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.dto.BestExchangeRateDto
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.service.WalletService
import java.math.BigDecimal
import java.time.LocalDateTime

class ExchangeOrderServiceImplTest : BehaviorSpec({
    val exchangeOrderRepository = mockk<ExchangeOrderRepository>()
    val exchangeService = mockk<ExchangeService>()
    val transactionService = mockk<TransactionService>()
    val ledgerService = mockk<ExchangeLedgerService>()
    val currencyRepository = mockk<CurrencyRepository>()
    val walletService = mockk<WalletService>()
    val accountRepository = mockk<AccountRepository>()

    val sut = ExchangeOrderServiceImpl(
        exchangeOrderRepository = exchangeOrderRepository,
        exchangeService = exchangeService,
        transactionService = transactionService,
        ledgerService = ledgerService,
        currencyRepository = currencyRepository,
        walletService = walletService,
        accountRepository = accountRepository
    )

    val krw = Currency(id = 1L, code = "KRW", name = "대한민국 원", scale = 0, unit = BigDecimal.ONE)
    val usd = Currency(id = 2L, code = "USD", name = "미국 달러", scale = 2, unit = BigDecimal.ONE)
    val jpy = Currency(id = 3L, code = "JPY", name = "일본 엔", scale = 0, unit = BigDecimal("100"))

    beforeTest {
        clearAllMocks()
    }

    Given("정상적으로 buyOrder가 호출되면") {
        val userId = 10L
        val currencyCode = "USD"
        val fromAmount = BigDecimal("100000") // 원화 입력
        val bestBuy =
            BestExchangeRateDto(bankId = 1L, currencyId = usd.id!!, bestRate = BigDecimal("1300.00"), ExchangeType.BUY)

        // save 결과로 돌아올 엔티티 (id만 있으면 충분)
        val savedOrder = ExchangeOrder(
            id = 11L,
            userId = userId,
            bankId = bestBuy.bankId,
            fromCurrencyId = krw.id!!,
            toCurrencyId = usd.id!!,
            fromAmount = fromAmount,
            toAmount = BigDecimal.ZERO,
            exchangeRate = bestBuy.bestRate,
            status = OrderStatus.PENDING,
            requestedAt = LocalDateTime.now()
        )

        When("일반 구매일 경우") {
            // 통화 조회
            every { currencyRepository.findByCode("KRW") } returns krw
            every { currencyRepository.findByCode(currencyCode) } returns usd

            // 최적 환율
            every { exchangeService.getBestBuyRate(currencyCode) } returns bestBuy

            // 수수료 계산
//            val commission = fromAmount.multiply(BigDecimal("0.0005")).setScale(0, RoundingMode.UP)
//            val exchangeAmount = fromAmount - commission

            // 환전 결과
            every {
                exchangeService.calculateExchange(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns (BigDecimal("77.00") to BigDecimal.ZERO)

            // 주문 저장
            every { exchangeOrderRepository.save(any()) } returns savedOrder

            // 금액 이동
            every { walletService.userToCompany(any(), any(), any(), any()) }
            every { walletService.userToCompany(any(), any(), any(), any()) }

            every { walletService.bankToCompany(any(), any(), any()) }
            every { walletService.bankToCompany(any(), any(), any()) }

            every { walletService.companyToUser(any(), any(), any(), any()) }

            // 기록 저장
            verify(exactly = 2) { transactionService.record(any()) }
            verify(exactly = 1) { ledgerService.record(any()) }

            val orderResponse = sut.buyOrder(userId, currencyCode, fromAmount, false)

            Then("주문 상태는 SUCCESS") {
                orderResponse.status shouldBe OrderStatus.SUCCESS
                orderResponse.toAmount shouldBe BigDecimal("77.00")
            }
        }


    }
    Given("buyOrder 최소 금액 검증") {
        val userId = 99L
        val currencyCode = "USD"
        val bestBuy =
            BestExchangeRateDto(bankId = 1L, currencyId = usd.id!!, bestRate = BigDecimal("1300.00"), ExchangeType.BUY)

        When("fromAmount < bestRate 이면 예외 발생") {
            every { currencyRepository.findByCode("KRW") } returns krw
            every { currencyRepository.findByCode(currencyCode) } returns usd
            every { exchangeService.getBestBuyRate(currencyCode) } returns bestBuy

            Then("IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    sut.buyOrder(userId, currencyCode, BigDecimal("1000"), false)
                }
            }
        }
    }


})
