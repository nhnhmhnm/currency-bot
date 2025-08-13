package org.example.backend.user.service

import org.example.backend.common.util.toDTO
import org.example.backend.user.domain.DepositWithdrawal
import org.example.backend.user.dto.DepositWithdrawalCommand
import org.example.backend.user.dto.DepositWithdrawalResponse
import org.example.backend.user.repository.DepositWithdrawalRepository
import org.springframework.stereotype.Service

@Service
class DepositWithdrawalServiceImpl (
  private val depositWithdrawalRepository: DepositWithdrawalRepository
) : DepositWithdrawalService {
  override fun record(depositWithdrawal: DepositWithdrawalCommand): DepositWithdrawalResponse {
    val entity = DepositWithdrawal(
      userId = depositWithdrawal.userId,
      walletId = depositWithdrawal.walletId,
      currencyId = depositWithdrawal.currencyId,
      amount = depositWithdrawal.amount,
      type = depositWithdrawal.type,
    )
    val saved = depositWithdrawalRepository.save(entity)

    return saved.toDTO()
  }

}