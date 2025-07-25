package org.example.backend.user.service

import jakarta.transaction.Transactional
import org.example.backend.user.domain.User
import org.example.backend.user.dto.UserSignupRequest
import org.example.backend.user.dto.UserSignupResponse
import org.example.backend.user.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService (
  private val userRepository: UserRepository,
  private val passwordEncoder: PasswordEncoder
) {
  @Transactional
  fun createUser(request: UserSignupRequest): UserSignupResponse {
    if (userRepository.existsByDevice(request.device)) {
      throw IllegalArgumentException("이미 등록된 Device입니다.")
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
      id = savedUser.id!!,
      device = savedUser.device,
      name = savedUser.name,
      phone = savedUser.phone,
      type = savedUser.type,
      isActive = savedUser.isActive ?: true,
      createdAt = savedUser.createdAt
    )
  }
}