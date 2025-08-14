package org.example.backend.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.example.backend.enums.WalletTransactionStatusType
import org.example.backend.enums.WalletTransactionType
import org.example.backend.finance.domain.Currency
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

  @Column(name = "executed_at", updatable = false)
  var executedAt: LocalDateTime = LocalDateTime.now(),

  // 선택적(읽기 전용) 연관: 필요하면 유지
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  val user: User? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wallet_id", insertable = false, updatable = false)
  val wallet: Wallet? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "currency_id", insertable = false, updatable = false)
  val currency: Currency? = null
)