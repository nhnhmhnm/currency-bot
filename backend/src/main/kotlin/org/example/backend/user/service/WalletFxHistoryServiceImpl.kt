package org.example.backend.user.service

import org.example.backend.common.util.toDTO
import org.example.backend.user.domain.WalletFxHistory
import org.example.backend.user.dto.WalletFxCommand
import org.example.backend.user.dto.WalletFxHistoryResponse
import org.example.backend.user.repository.WalletFxHistoryRepository
import org.springframework.stereotype.Service

@Service
class WalletFxHistoryServiceImpl (
  private val walletFxHistoryRepository: WalletFxHistoryRepository
) : WalletFxHistoryService {
  override fun record(walletFx: WalletFxCommand): WalletFxHistoryResponse {
    val entity = WalletFxHistory(
      walletId = walletFx.walletId,
      currencyId = walletFx.currencyId,
      orderId = walletFx.orderId,
      amount = walletFx.amount,
      balanceAfter = walletFx.balanceAfter,
      type = walletFx.type,
      executedAt = walletFx.executedAt
    )
    val saved = walletFxHistoryRepository.save(entity)

    return saved.toDTO()
  }

}