package org.example.backend.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "account")
class Account(
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @Column(name = "user_id")
  val userId: Long,

  @Column(name = "bank_id", nullable = false)
  val bankId: Long,

  @Column(name = "currency_id", nullable = false)
  val currencyId: Long,

  @Column(name = "account_num", nullable = false, length = 50)
  val accountNum: String,

  @Column(name = "balance")
  val balance: BigDecimal = BigDecimal.ZERO,

  @Column(name = "created_at")
  val createdAt: LocalDateTime = LocalDateTime.now()
)