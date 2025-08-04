package org.example.backend.user.controller

import org.example.backend.auth.dto.UserPrincipal
import org.example.backend.user.dto.AccountConnectRequest
import org.example.backend.user.service.WalletServiceImpl
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/wallet")
class WalletController(
  private val walletService: WalletServiceImpl
) {
  @PostMapping("/connect")
  fun connectAccount(
    @AuthenticationPrincipal user: UserPrincipal,
    @RequestBody request: AccountConnectRequest): ResponseEntity<String> {

    walletService.connectAccount(
      userId = user.id,
      bankId = request.bankId,
      accountNum = request.accountNum
    )

    return ResponseEntity.ok("Account connected and wallet activated")
  }
}