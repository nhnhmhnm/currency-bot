package org.example.backend.finance.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "exchange_rate")
data class ExchangeRate(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    val bank: Bank,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id")
    val currency: Currency,

    @Column(name = "base_rate", nullable = false)
    val baseRate: BigDecimal,

    @Column(name = "buy_rate")
    val buyRate: BigDecimal? = null,

    @Column(name = "sell_rate")
    val sellRate: BigDecimal? = null,

    @Column(name = "notice_time", nullable = false)
    val noticeTime: LocalDateTime,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
