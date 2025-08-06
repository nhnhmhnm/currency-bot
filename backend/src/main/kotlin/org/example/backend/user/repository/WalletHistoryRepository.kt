package org.example.backend.user.repository

import org.example.backend.user.domain.WalletFxHistory
import org.springframework.data.jpa.repository.JpaRepository

interface WalletHistoryRepository : JpaRepository<WalletFxHistory, Long> {
    fun findByWalletId(walletId: Long): List<WalletFxHistory>?
}
