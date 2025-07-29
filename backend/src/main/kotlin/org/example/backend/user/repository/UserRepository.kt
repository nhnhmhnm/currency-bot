package org.example.backend.user.repository

import org.example.backend.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByDevice(device: String): User?
}
