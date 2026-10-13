package org.example.backend.user.domain

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "wallet",
    uniqueConstraints = [UniqueConstraint(name = "uk_wallet_user_currency", columnNames = ["user_id", "currency_id"])]
)
class Wallet(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "currency_id", nullable = false)
    val currencyId: Long,

    @Column(name = "account_id")
    var accountId: Long? = null,

    @Column(name = "balance", nullable = false, precision = 24, scale = 8)
    var balance: BigDecimal = BigDecimal.ZERO
)
