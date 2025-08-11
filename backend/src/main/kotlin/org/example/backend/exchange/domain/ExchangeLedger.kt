package org.example.backend.exchange.domain

import jakarta.persistence.*
import org.example.backend.enums.ExchangeLedgerType
import org.hibernate.annotations.CreationTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "exchange_ledger")
class ExchangeLedger(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "from_currency_id", nullable = false)
    val fromCurrencyId: Long,

    @Column(name = "to_currency_id", nullable = false)
    val toCurrencyId: Long,

    @Column(name = "from_amount", nullable = false)
    val fromAmount: BigDecimal,

    @Column(name = "to_amount", nullable = false)
    val toAmount: BigDecimal,

    @Column(name = "exchange_rate", nullable = false)
    val exchangeRate: BigDecimal,

    @Column(name = "commission_currency_id", nullable = true)
    val commissionCurrencyId: Long? = null,

    @Column(name = "commission_rate")
    val commissionRate: BigDecimal?,

    @Column(name = "commission_amount", nullable = true)
    val commissionAmount: BigDecimal? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: ExchangeLedgerType,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null
)
