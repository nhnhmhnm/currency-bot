package org.example.backend.user.repository

import jakarta.persistence.LockModeType
import org.example.backend.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.*

interface UserRepository: JpaRepository<User, Long> {
  fun existsByDevice(device: String): Boolean
  fun findByDevice(device: String): User?

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  fun findByIdForUpdate(id: Long): User?
}
