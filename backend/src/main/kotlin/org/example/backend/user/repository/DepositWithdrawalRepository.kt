package org.example.backend.user.repository

import org.example.backend.user.domain.DepositWithdrawal
import org.springframework.data.jpa.repository.JpaRepository

interface DepositWithdrawalRepository : JpaRepository<DepositWithdrawal, Long> {
}