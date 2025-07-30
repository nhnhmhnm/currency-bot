package org.example.backend.auth.controller

import org.example.backend.auth.dto.RefreshTokenRequest
import org.example.backend.auth.dto.TokenResponse
import org.example.backend.auth.dto.UserLoginRequest
import org.example.backend.auth.service.RedisTokenService
import org.example.backend.common.jwt.JwtHeaderUtil
import org.example.backend.common.jwt.JwtTokenProvider
import org.example.backend.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController (
  private val userService: UserService,
  private val jwtTokenProvider: JwtTokenProvider,
  private val redisTokenService: RedisTokenService
) {
  @PostMapping("/login")
  fun login(@RequestBody request: UserLoginRequest): ResponseEntity<TokenResponse> {
    val token = userService.login(request)
    return ResponseEntity.ok(token)
  }

  @PostMapping("/reissue")
  fun reissueToken(@RequestBody request: RefreshTokenRequest)
  : ResponseEntity<TokenResponse> {
    val refreshToken = request.refreshToken
    val userId = jwtTokenProvider.getUserId(refreshToken)

    // 유효성 검사
    if (!jwtTokenProvider.validateToken(refreshToken)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
    }

    // Redis에서 refreshToken 일치 여부 확인
    val storedToken = redisTokenService.getRefreshToken(userId)
    if (storedToken != refreshToken) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
    }

    // 새로운 AccessToken 발급
    val newAccessToken = jwtTokenProvider.createAccessToken(userId)
    redisTokenService.saveAccessToken(userId, newAccessToken, jwtTokenProvider.getAccessExpiration())

    return ResponseEntity.ok(TokenResponse(newAccessToken, refreshToken)
    )
  }
}