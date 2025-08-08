package org.example.backend.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "account")
class Account(
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  val user: User? = null,

  @Column(name = "user_id", nullable = false)
  val userId: Long,

  @Column(name = "bank_id", nullable = false)
  val bankId: Long,

  @Column(name = "currency_id", nullable = false)
  val currencyId: Long,

  @Column(name = "account_num", nullable = false, length = 50)
  val accountNum: String,

  @Column(name = "balance")
  var balance: BigDecimal = BigDecimal("1000000000.00"), // 10억원

  @Column(name = "is_active")
  var isActive: Boolean = true,

  @Column(name = "created_at")
  val createdAt: LocalDateTime = LocalDateTime.now()
)