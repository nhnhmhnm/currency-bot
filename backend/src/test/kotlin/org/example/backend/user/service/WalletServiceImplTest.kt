package org.example.backend.user.service

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.exchange.repository.ExchangeReservationRepository
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.finance.repository.InternalAccountRepository
import org.example.backend.finance.service.JournalService
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.SimulatedBankBalanceRepository
import org.example.backend.user.repository.UserRepository
import org.example.backend.user.repository.WalletRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class WalletServiceImplTest {
    @Test
    fun `deposit and withdrawal fail closed without a bank integration`() {
        val service = WalletServiceImpl(
            walletRepository = mockk<WalletRepository>(),
            accountRepository = mockk<AccountRepository>(),
            internalAccountRepository = mockk<InternalAccountRepository>(),
            simulatedBankBalanceRepository = mockk<SimulatedBankBalanceRepository>(),
            userRepository = mockk<UserRepository>(),
            currencyRepository = mockk<CurrencyRepository>(),
            reservationRepository = mockk<ExchangeReservationRepository>(),
            depositWithdrawalService = mockk<DepositWithdrawalService>(),
            journalService = mockk<JournalService>(),
            bankSimulationEnabled = false
        )

        assertThrows<UserException> {
            service.depositFromAccount(1L, 1L, BigDecimal.ONE)
        }.errorCode shouldBe ErrorCode.BANK_INTEGRATION_UNAVAILABLE.code
        assertThrows<UserException> {
            service.withdrawToAccount(1L, 1L, BigDecimal.ONE)
        }.errorCode shouldBe ErrorCode.BANK_INTEGRATION_UNAVAILABLE.code
    }
}
