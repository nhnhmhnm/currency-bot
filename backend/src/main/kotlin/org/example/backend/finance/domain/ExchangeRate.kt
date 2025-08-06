package org.example.backend.finance.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "exchange_rate")
class ExchangeRate(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "bank_id", nullable = false)
    val bankId: Long,

    @Column(name = "currency_id", nullable = false)
    val currencyId: Long,

    @Column(name = "base_rate", nullable = false)
    val baseRate: BigDecimal,

    @Column(name = "buy_rate")
    val buyRate: BigDecimal,

    @Column(name = "sell_rate")
    val sellRate: BigDecimal,

    @Column(name = "notice_time", nullable = false)
    val noticeTime: LocalDateTime,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
