package org.example.backend.user.service

import jakarta.transaction.Transactional
import org.example.backend.user.domain.User
import org.example.backend.user.dto.UserLoginRequest
import org.example.backend.user.dto.UserSignupRequest
import org.example.backend.user.dto.UserSignupResponse
import org.example.backend.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
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
      id = savedUser.id ?: throw IllegalStateException("ID 생성 실패"),
      device = savedUser.device,
      name = savedUser.name,
      phone = savedUser.phone,
      type = savedUser.type,
      isActive = savedUser.isActive,
      createdAt = savedUser.createdAt
    )
  }

  fun login(request: UserLoginRequest): User {
    val user = userRepository.findByDevice(request.device)
      ?: throw java.lang.IllegalArgumentException("존재하지 않는 사용자입니다")

    if (!passwordEncoder.matches(request.password, user.password)) {
      throw IllegalArgumentException("비밀번호 불일치")
    }

    return user
  }
}