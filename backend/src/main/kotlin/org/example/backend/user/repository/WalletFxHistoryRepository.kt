package org.example.backend.user.repository

import org.example.backend.user.domain.WalletFxHistory
import org.springframework.data.jpa.repository.JpaRepository

interface WalletFxHistoryRepository : JpaRepository<WalletFxHistory, Long>{
  fun findFirstByWalletIdOrderByExecutedAtDesc(walletId: Long): WalletFxHistory?
}