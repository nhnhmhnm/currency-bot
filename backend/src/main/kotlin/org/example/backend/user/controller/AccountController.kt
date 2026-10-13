package org.example.backend.user.controller

import org.example.backend.auth.dto.UserPrincipal
import org.example.backend.user.dto.AccountCreateRequest
import org.example.backend.user.service.AccountService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/account")
class AccountController(
  private val accountService: AccountService
) {
  @PostMapping
  fun createAccount(
    @AuthenticationPrincipal user: UserPrincipal,
    @RequestBody request: AccountCreateRequest
  ): ResponseEntity<String> {
    accountService.createAccount(user.id, request)
    return ResponseEntity.status(HttpStatus.CREATED).body("계좌 생성 성공")
  }
}
