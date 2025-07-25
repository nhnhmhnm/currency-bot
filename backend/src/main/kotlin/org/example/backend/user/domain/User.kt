package org.example.backend.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.example.backend.user.type.UserType
import java.time.LocalDateTime

@Entity
@Table(name = "user")
class User (
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @Column(name = "device", nullable = false)
  val device: String,

  @Column(name = "password", nullable = false)
  val password: String,

  @Column(name = "name", nullable = false)
  val name: String,

  @Column(name = "phone", nullable = false)
  val phone: String,

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  val type: UserType,

  @Column(name = "isActive")
  val isActive: Boolean? = true,

  @Column(name = "created_at")
  val createdAt: LocalDateTime = LocalDateTime.now(),
)