package org.example.backend.user.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "wallet")
data class Wallet(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id")
    val userId: Long,

    @Column(name = "currency_id")
    val currencyId: Long,

    val balance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
