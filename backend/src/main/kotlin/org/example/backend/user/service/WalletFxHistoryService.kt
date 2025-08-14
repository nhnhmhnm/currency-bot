package org.example.backend.user.service

import org.example.backend.user.dto.WalletFxCommand
import org.example.backend.user.dto.WalletFxHistoryResponse

interface WalletFxHistoryService {
  fun record(walletFx: WalletFxCommand): WalletFxHistoryResponse
}