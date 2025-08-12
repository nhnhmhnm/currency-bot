package org.example.backend.exchange.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.example.backend.enums.ExchangeLedgerType
import org.example.backend.exchange.domain.ExchangeLedger
import org.example.backend.exchange.dto.ExchangeLedgerCommand
import org.example.backend.exchange.repository.ExchangeLedgerRepository
import java.math.BigDecimal
import java.time.LocalDateTime

class ExchangeLedgerServiceImplTest : BehaviorSpec({
    val repository = mockk<ExchangeLedgerRepository>()
    val service = ExchangeLedgerServiceImpl(repository)

    Given("ExchangeLedgerCommand가 주어졌을 때") {
        val dto = ExchangeLedgerCommand(
            userId = 10L,
            fromCurrencyId = 1L,
            toCurrencyId = 3L,
            fromAmount = BigDecimal("100000"),
            toAmount = BigDecimal("10599"),
            exchangeRate = BigDecimal("943"),
            commissionCurrencyId = 1L,
            commissionRate = BigDecimal("0.0005"),
            commissionAmount = BigDecimal("50.0000"),
            type = ExchangeLedgerType.FX_BUY
        )

        // repository.save 인자로 넘어가는 엔티티를 캡처해서 검증
        val savedArg = slot<ExchangeLedger>()

        // 저장 결과
        val savedLedger = ExchangeLedger(
            id = 77L,
            userId = dto.userId,
            fromCurrencyId = dto.fromCurrencyId,
            toCurrencyId = dto.toCurrencyId,
            fromAmount = dto.fromAmount,
            toAmount = dto.toAmount,
            exchangeRate = dto.exchangeRate,
            commissionCurrencyId = dto.commissionCurrencyId,
            commissionRate = dto.commissionRate,
            commissionAmount = dto.commissionAmount,
            type = dto.type,
            createdAt = LocalDateTime.of(2025, 8, 7, 18, 0, 0)
        )

        every { repository.save(capture(savedArg)) } returns savedLedger

        When("record를 호출하면") {
            val result = service.record(dto)

            Then("repository.save에 DTO가 올바르게 매핑되어 전달된다.") {
                // 입력 → 엔티티 매핑 검증
                savedArg.captured.userId shouldBe dto.userId
                savedArg.captured.fromCurrencyId shouldBe dto.fromCurrencyId
                savedArg.captured.toCurrencyId shouldBe dto.toCurrencyId
                savedArg.captured.fromAmount shouldBe dto.fromAmount
                savedArg.captured.toAmount shouldBe dto.toAmount
                savedArg.captured.exchangeRate shouldBe dto.exchangeRate
                savedArg.captured.commissionCurrencyId shouldBe dto.commissionCurrencyId
                savedArg.captured.commissionRate shouldBe dto.commissionRate
                savedArg.captured.commissionAmount shouldBe dto.commissionAmount
                savedArg.captured.type shouldBe dto.type

                verify(exactly = 1) { repository.save(any()) }
            }

            Then("서비스 결과는 저장된 엔티티의 값과 일치한다.") {
                // 결과 → 저장 결과 객체로 비교
                result.userId shouldBe savedLedger.userId
                result.fromCurrencyId shouldBe savedLedger.fromCurrencyId
                result.toCurrencyId shouldBe savedLedger.toCurrencyId
                result.fromAmount shouldBe savedLedger.fromAmount
                result.toAmount shouldBe savedLedger.toAmount
                result.exchangeRate shouldBe savedLedger.exchangeRate
                result.commissionCurrencyId shouldBe savedLedger.commissionCurrencyId
                result.commissionRate shouldBe savedLedger.commissionRate
                result.commissionAmount shouldBe savedLedger.commissionAmount
                result.type shouldBe savedLedger.type
                result.createdAt shouldBe savedLedger.createdAt
            }
        }
    }
})
