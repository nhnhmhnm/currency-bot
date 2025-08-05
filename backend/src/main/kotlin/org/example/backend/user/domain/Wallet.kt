package org.example.backend.user.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "wallet")
class Wallet(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "currency_id", nullable = false)
    val currencyId: Long,

    @Column(name = "account_id")
    var accountId: Long? = null,

    @Column(name = "balance")
    val balance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "is_connected")
    var isConnected: Boolean = false
)
