package org.example.backend.exchange.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.assertThrows
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.example.backend.enums.CompanyAccount
import org.example.backend.enums.WalletFxHistoryType
import org.example.backend.finance.domain.InternalAccount
import org.example.backend.finance.repository.InternalAccountRepository
import org.example.backend.user.domain.Wallet
import org.example.backend.user.repository.WalletRepository
import org.example.backend.user.service.WalletFxHistoryService
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PostingPlanTest {
    @Test
    fun `unbalanced posting plan is rejected before any balance lock`() {
        assertThrows<IllegalArgumentException> {
            PostingPlan(
                1L,
                10L,
                listOf(WalletPosting(1L, BigDecimal("-100"), WalletFxHistoryType.USER_TO_COMPANY)),
                listOf(AccountPosting(CompanyAccount.KRW_ACCOUNT, BigDecimal("-99")))
            )
        }
    }

    @Test
    fun `buy plan separates principal fee and purchased currency`() {
        val plan = PostingPlan.buy(
            1L, 10L, 1L, 2L,
            BigDecimal("100000"), BigDecimal("99997"), BigDecimal("76.92"), BigDecimal("3")
        )

        plan.walletPostings.map { it.delta } shouldBe listOf(
            BigDecimal("-99997"), BigDecimal("-3"), BigDecimal("76.92")
        )
        plan.walletPostings.map { it.historyType } shouldBe listOf(
            WalletFxHistoryType.USER_TO_COMPANY,
            WalletFxHistoryType.USER_TO_COMPANY,
            WalletFxHistoryType.COMPANY_TO_USER
        )
        plan.accountPostings.associate { it.code to it.delta } shouldBe mapOf(
            CompanyAccount.KRW_ACCOUNT to BigDecimal("-99997"),
            CompanyAccount.USD_ACCOUNT to BigDecimal("76.92"),
            CompanyAccount.COMMISSION_ACCOUNT to BigDecimal("3")
        )
    }

    @Test
    fun `sell plan credits gross proceeds and separates customer payout from fee`() {
        val plan = PostingPlan.sell(
            2L, 10L, 2L, 1L,
            BigDecimal("10"), BigDecimal("13000"), BigDecimal("12999"), BigDecimal.ONE
        )

        plan.walletPostings.map { it.delta } shouldBe listOf(BigDecimal("-10"), BigDecimal("12999"))
        plan.accountPostings.associate { it.code to it.delta } shouldBe mapOf(
            CompanyAccount.USD_ACCOUNT to BigDecimal("-10"),
            CompanyAccount.KRW_ACCOUNT to BigDecimal("13000"),
            CompanyAccount.COMMISSION_ACCOUNT to BigDecimal.ONE
        )
    }

    @Test
    fun `posting locks wallets by currency then company accounts by code`() {
        val walletRepository = mockk<WalletRepository>()
        val accountRepository = mockk<InternalAccountRepository>()
        val historyService = mockk<WalletFxHistoryService>(relaxed = true)
        val krwWallet = Wallet(id = 1L, userId = 10L, currencyId = 1L, balance = BigDecimal.ZERO)
        val usdWallet = Wallet(id = 2L, userId = 10L, currencyId = 2L, balance = BigDecimal("100"))
        every { walletRepository.findByUserIdAndCurrencyIdForUpdate(10L, 1L) } returns krwWallet
        every { walletRepository.findByUserIdAndCurrencyIdForUpdate(10L, 2L) } returns usdWallet
        for (code in listOf(CompanyAccount.COMMISSION_ACCOUNT, CompanyAccount.KRW_ACCOUNT, CompanyAccount.USD_ACCOUNT)) {
            every { accountRepository.findByCodeForUpdate(code) } returns InternalAccount(
                id = code.ordinal.toLong() + 1,
                code = code,
                currencyId = code.currencyId,
                balance = BigDecimal("100000")
            )
        }

        PostingService(walletRepository, accountRepository, historyService, mockk(relaxed = true),
            ExchangeOperationsSnapshotService(mockk(), mockk())).post(
            PostingPlan.sell(
                3L, 10L, 2L, 1L,
                BigDecimal("10"), BigDecimal("13000"), BigDecimal("12999"), BigDecimal.ONE
            )
        )

        verifyOrder {
            walletRepository.findByUserIdAndCurrencyIdForUpdate(10L, 1L)
            walletRepository.findByUserIdAndCurrencyIdForUpdate(10L, 2L)
            accountRepository.findByCodeForUpdate(CompanyAccount.COMMISSION_ACCOUNT)
            accountRepository.findByCodeForUpdate(CompanyAccount.KRW_ACCOUNT)
            accountRepository.findByCodeForUpdate(CompanyAccount.USD_ACCOUNT)
        }
        krwWallet.balance.compareTo(BigDecimal("12999")) shouldBe 0
        usdWallet.balance.compareTo(BigDecimal("90")) shouldBe 0
    }
}
