package org.example.backend.user.repository

import org.example.backend.user.domain.WalletHistory
import org.springframework.data.jpa.repository.JpaRepository

interface WalletHistoryRepository : JpaRepository<WalletHistory, Long> {
    fun findByWalletId(walletId: Long): List<WalletHistory>?
}
