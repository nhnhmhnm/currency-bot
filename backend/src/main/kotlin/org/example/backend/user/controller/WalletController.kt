package org.example.backend.user.controller

import org.example.backend.user.dto.AccountRegistrationRequest
import org.example.backend.user.service.WalletServiceImpl
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/wallet")
class WalletController(
  private val walletService: WalletServiceImpl
) {
  @PostMapping("/registration")
  fun registerAccount(@RequestBody request: AccountRegistrationRequest,
                      authentication: Authentication): ResponseEntity<String> {
    // 로직 구현 해야 함

    return ResponseEntity.ok("Account registered and wallet activated")
  }
}