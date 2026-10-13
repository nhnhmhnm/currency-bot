package org.example.backend.user.service

import org.example.backend.enums.UserType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.finance.repository.BankRepository
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.user.domain.Account
import org.example.backend.user.domain.SimulatedBankBalance
import org.example.backend.user.dto.AccountCreateRequest
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.SimulatedBankBalanceRepository
import org.example.backend.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountServiceImpl(
    private val accountRepository: AccountRepository,
    private val simulatedBankBalanceRepository: SimulatedBankBalanceRepository,
    private val userRepository: UserRepository,
    private val bankRepository: BankRepository,
    private val currencyRepository: CurrencyRepository
) : AccountService {
    @Transactional
    override fun createAccount(userId: Long, request: AccountCreateRequest) {
        val user = userRepository.findById(userId).orElseThrow { UserException(ErrorCode.USER_NOT_FOUND) }
        if (!user.isActive || user.type != UserType.GENERAL) {
            throw UserException(ErrorCode.INCORRECT_OWNER)
        }
        if (request.accountNum.isBlank() || !bankRepository.existsById(request.bankId)) {
            throw UserException(ErrorCode.ACCOUNT_NOT_FOUND)
        }
        if (!currencyRepository.existsById(request.currencyId)) {
            throw UserException(ErrorCode.CURRENCY_MISMATCH)
        }
        if (accountRepository.findByBankIdAndAccountNum(request.bankId, request.accountNum) != null) {
            throw UserException(ErrorCode.DUPLICATED_ACCOUNT)
        }

        val account = accountRepository.save(
            Account(
                userId = userId,
                bankId = request.bankId,
                currencyId = request.currencyId,
                accountNum = request.accountNum
            )
        )
        simulatedBankBalanceRepository.save(SimulatedBankBalance(accountId = account.id!!))
    }
}
