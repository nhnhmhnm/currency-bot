package org.example.backend.user.service

import jakarta.transaction.Transactional
import org.example.backend.common.jwt.JwtTokenProvider
import org.example.backend.common.jwt.RedisTokenService
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.user.domain.User
import org.example.backend.user.dto.UserLoginRequest
import org.example.backend.user.dto.UserSignupRequest
import org.example.backend.user.dto.UserSignupResponse
import org.example.backend.user.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService (
  private val userRepository: UserRepository,
  private val passwordEncoder: PasswordEncoder,
  private val jwtTokenProvider: JwtTokenProvider,
  private val redisTokenService: RedisTokenService,

  @Value("\${jwt.expiration}")
  private val expiration: Long
) {
  @Transactional
  fun createUser(request: UserSignupRequest): UserSignupResponse {
    if (userRepository.existsByDevice(request.device)) {
      throw UserException(HttpStatus.BAD_REQUEST, ErrorCode.DUPLICATED_USER_DEVICE)
    }

    val user = User(
      device = request.device,
      name = request.name,
      password = passwordEncoder.encode(request.password),
      phone = request.phone,
      type = request.type
    )

    val savedUser = userRepository.save(user)

    return UserSignupResponse(
      id = savedUser.id ?: throw UserException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.FAILED_TO_CREATE_USER_ID),
      device = savedUser.device,
      name = savedUser.name,
      phone = savedUser.phone,
      type = savedUser.type,
      isActive = savedUser.isActive,
      createdAt = savedUser.createdAt
    )
  }

  fun login(request: UserLoginRequest): String {
    val user = userRepository.findByDevice(request.device)
      ?: throw IllegalArgumentException("존재하지 않는 사용자")

    if (!passwordEncoder.matches(request.password, user.password)) {
      throw IllegalArgumentException("비밀번호 불 일치")
    }

    val token = jwtTokenProvider.createToken(user.id!!)
    redisTokenService.saveToken(user.id!!, token, expiration)

    return token
  }
}