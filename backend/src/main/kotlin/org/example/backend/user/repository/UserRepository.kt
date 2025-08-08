package org.example.backend.user.repository

import org.example.backend.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UserRepository: JpaRepository<User, Long> {
  fun existsByDevice(device: String): Boolean
  fun findByDevice(device: String): User?
}