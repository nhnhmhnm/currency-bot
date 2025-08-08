package org.example.backend.user.service

import jakarta.transaction.Transactional
import org.example.backend.common.jwt.JwtTokenProvider
import org.example.backend.auth.service.RedisTokenService
import org.example.backend.auth.dto.TokenResponse
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.user.domain.User
import org.example.backend.auth.dto.UserLoginRequest
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.user.domain.Wallet
import org.example.backend.user.dto.UserMeResponse
import org.example.backend.user.dto.UserSignupRequest
import org.example.backend.user.dto.UserSignupResponse
import org.example.backend.user.repository.UserRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserServiceImpl (
  private val userRepository: UserRepository,
  private val passwordEncoder: PasswordEncoder,
  private val jwtTokenProvider: JwtTokenProvider,
  private val redisTokenService: RedisTokenService,

  private val walletRepository: WalletRepository,
  private val currencyRepository: CurrencyRepository
) : UserService{
  @Transactional
  override fun createUser(request: UserSignupRequest): UserSignupResponse {
    if (userRepository.existsByDevice(request.device)) {
      throw UserException(ErrorCode.DUPLICATED_USER_DEVICE)
    }

    val user = User(
      device = request.device,
      name = request.name,
      password = passwordEncoder.encode(request.password),
      phone = request.phone,
      type = request.type
    )

    val savedUser = userRepository.save(user)

    // 통화별 기본 wallet 생성
    val currencies = listOf("KRW", "USD", "JPY")
    val wallets = currencies.map { code ->
      val currency = currencyRepository.findByCode(code) ?: throw IllegalArgumentException("잘못된 통화 코드")

      Wallet(userId = savedUser.id!!, currencyId = currency.id!!, isConnected = false)
    }

    walletRepository.saveAll(wallets)

    return UserSignupResponse(
      id = savedUser.id ?: throw UserException(ErrorCode.FAILED_TO_CREATE_USER_ID),
      device = savedUser.device,
      name = savedUser.name,
      phone = savedUser.phone,
      type = savedUser.type,
      isActive = savedUser.isActive,
      createdAt = savedUser.createdAt
    )
  }

  override fun login(request: UserLoginRequest): TokenResponse {
    val user = userRepository.findByDevice(request.device)
      ?: throw IllegalArgumentException("존재하지 않는 사용자")

    if (!passwordEncoder.matches(request.password, user.password)) {
      throw IllegalArgumentException("비밀번호 불 일치")
    }

    // Token 생성
    val accessToken = jwtTokenProvider.createAccessToken(user.id!!)
    val refreshToken = jwtTokenProvider.createRefreshToken(user.id!!)

    // Redis 저장
    redisTokenService.saveAccessToken(user.id!!, accessToken, jwtTokenProvider.getAccessExpiration())
    redisTokenService.saveRefreshToken(user.id!!, refreshToken, jwtTokenProvider.getRefreshExpiration())

    return TokenResponse(accessToken, refreshToken)
  }

  override fun getMyInfo(userId: Long): UserMeResponse {
    val user = userRepository.findById(userId)
      .orElseThrow{ IllegalArgumentException("존재하지 않는 사용자") }

    return UserMeResponse(userId, user.device, user.name, user.phone,
      user.type, user.isActive, user.createdAt)
  }
}