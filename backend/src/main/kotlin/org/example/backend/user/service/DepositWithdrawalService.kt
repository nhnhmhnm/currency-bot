package org.example.backend.user.service

import org.example.backend.user.dto.DepositWithdrawalCommand
import org.example.backend.user.dto.DepositWithdrawalResponse

interface DepositWithdrawalService {
  fun record(depositWithdrawal: DepositWithdrawalCommand): DepositWithdrawalResponse
}