package org.example.backend.user.domain

import jakarta.persistence.*
import org.example.backend.finance.domain.Currency
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "wallet")
data class Wallet(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id")
    val currency: Currency,

    val balance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
