package org.example.backend.user.service

import org.example.backend.user.dto.AccountCreateRequest

interface AccountService {
  fun createAccount(request: AccountCreateRequest)
}