package org.example.backend.user.service

import org.example.backend.user.dto.AccountCreateRequest

interface AccountService {
  fun createAccount(userId: Long, request: AccountCreateRequest)
}
