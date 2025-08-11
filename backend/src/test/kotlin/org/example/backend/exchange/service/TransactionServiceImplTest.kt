package org.example.backend.exchange.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.example.backend.exchange.domain.Transaction
import org.example.backend.exchange.dto.TransactionCommand
import org.example.backend.exchange.repository.TransactionRepository
import org.mockito.Mockito.verify
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionServiceImplTest : BehaviorSpec({
    val repository = mockk<TransactionRepository>()
    val service = TransactionServiceImpl(repository)

    Given("TransactionDTO가 주어졌을 때") {
         val dto = TransactionCommand(
             userId = 1L,
             walletId = 2L,
             orderId = 3L,
             fromCurrencyId = 1L,
             toCurrencyId = 3L,
             fromAmount = BigDecimal("100000"),
             toAmount = BigDecimal("10599"),
             exchangeRate = BigDecimal("943"),
             commissionCurrencyId = 1L,
             commissionAmount = BigDecimal("0.0005"),
             profitCurrencyId = 3L,
             profit = BigDecimal("0.151643")
        )

        // repository.save로 전달된 엔티티를 캡처해서 매핑 검증
        val savedArg = slot<Transaction>()
        
        // 저장 결과
        val savedTransaction = Transaction(
             id = 99L,
             userId = dto.userId,
             walletId = dto.walletId,
             orderId = dto.orderId,
             fromCurrencyId = dto.fromCurrencyId,
             toCurrencyId = dto.toCurrencyId,
             fromAmount = dto.fromAmount,
             toAmount = dto.toAmount,
             exchangeRate = dto.exchangeRate,
             commissionCurrencyId = dto.commissionCurrencyId,
             commissionAmount = dto.commissionAmount,
             profitCurrencyId = dto.profitCurrencyId,
             profit = dto.profit,
            createdAt = LocalDateTime.of(2025, 8, 7, 17, 18, 34)
        )

        every { repository.save(capture(savedArg)) } returns savedTransaction

        When("record를 호출하면") {
            val result = service.record(dto)

            Then("repository.save에 DTO가 올바르게 매핑되어 전달된다.") {
                // 입력 → 엔티티 매핑 검증
                savedArg.captured.userId shouldBe dto.userId
                savedArg.captured.walletId shouldBe dto.walletId
                savedArg.captured.orderId shouldBe dto.orderId
                savedArg.captured.fromCurrencyId shouldBe dto.fromCurrencyId
                savedArg.captured.toCurrencyId shouldBe dto.toCurrencyId
                savedArg.captured.fromAmount shouldBe dto.fromAmount
                savedArg.captured.toAmount shouldBe dto.toAmount
                savedArg.captured.exchangeRate shouldBe dto.exchangeRate
                savedArg.captured.commissionCurrencyId shouldBe dto.commissionCurrencyId
                savedArg.captured.commissionAmount shouldBe dto.commissionAmount
                savedArg.captured.profitCurrencyId shouldBe dto.profitCurrencyId
                savedArg.captured.profit shouldBe dto.profit

                verify(exactly = 1) { repository.save(any()) }
            }

            Then("서비스 결과는 저장된 엔티티의 값과 일치한다.") {
                // 결과 → 저장 결과 객체로 비교
                result.id shouldBe savedTransaction.id
                result.userId shouldBe savedTransaction.userId
                result.walletId shouldBe savedTransaction.walletId
                result.orderId shouldBe savedTransaction.orderId
                result.fromCurrencyId shouldBe savedTransaction.fromCurrencyId
                result.toCurrencyId shouldBe savedTransaction.toCurrencyId
                result.fromAmount shouldBe savedTransaction.fromAmount
                result.toAmount shouldBe savedTransaction.toAmount
                result.exchangeRate shouldBe savedTransaction.exchangeRate
                result.commissionCurrencyId shouldBe savedTransaction.commissionCurrencyId
                result.commissionAmount shouldBe savedTransaction.commissionAmount
                result.profitCurrencyId shouldBe savedTransaction.profitCurrencyId
                result.profit shouldBe savedTransaction.profit
                result.createdAt shouldBe savedTransaction.createdAt
            }
        }
    }
})
