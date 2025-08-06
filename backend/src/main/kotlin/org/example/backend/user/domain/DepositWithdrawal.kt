package org.example.backend.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.example.backend.enums.WalletTransactionStatusType
import org.example.backend.enums.WalletTransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "deposit_withdrawal")
class DepositWithdrawal(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @Column(name = "user_id", nullable = false)
  val userId: Long,

  @Column(name = "wallet_id", nullable = false)
  val walletId: Long,

  @Column(name = "currency_id", nullable = false)
  val currencyId: Long,

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  val amount: BigDecimal,

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  val type: WalletTransactionType,

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  var status: WalletTransactionStatusType = WalletTransactionStatusType.PENDING,

  @Column(name = "status_desc", columnDefinition = "TEXT")
  var statusDesc: String? = null,

  @Column(name = "requested_at", nullable = false)
  val requestedAt: LocalDateTime = LocalDateTime.now(),

  @Column(name = "executed_at")
  var executedAt: LocalDateTime? = null
)