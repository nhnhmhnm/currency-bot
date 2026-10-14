package org.example.backend.exchange.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

enum class ReservationStatus { ACTIVE, CONSUMED, RELEASED }

@Entity
@Table(
    name = "exchange_reservation",
    indexes = [
        Index(name = "idx_reservation_funds", columnList = "user_id,from_currency_id,status"),
        Index(name = "idx_reservation_daily", columnList = "user_id,business_date,status")
    ]
)
class ExchangeReservation(
    @Id
    @Column(name = "order_id")
    val orderId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "from_currency_id", nullable = false)
    val fromCurrencyId: Long,

    @Column(name = "from_amount", nullable = false, precision = 24, scale = 8)
    val fromAmount: BigDecimal,

    @Column(name = "business_date", nullable = false)
    val businessDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: ReservationStatus = ReservationStatus.ACTIVE
)
