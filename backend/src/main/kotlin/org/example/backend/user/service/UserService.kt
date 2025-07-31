package org.example.backend.user.service

import org.example.backend.auth.dto.TokenResponse
import org.example.backend.auth.dto.UserLoginRequest
import org.example.backend.user.dto.UserMeResponse
import org.example.backend.user.dto.UserSignupRequest
import org.example.backend.user.dto.UserSignupResponse

interface UserService {
  fun createUser(request: UserSignupRequest): UserSignupResponse
  fun login(request: UserLoginRequest): TokenResponse
  fun getMyInfo(userId: Long): UserMeResponse
}
