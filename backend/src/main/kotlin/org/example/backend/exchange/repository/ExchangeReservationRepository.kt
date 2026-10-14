package org.example.backend.exchange.repository

import org.example.backend.exchange.domain.ExchangeReservation
import org.example.backend.exchange.domain.ReservationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal

interface ExchangeReservationRepository : JpaRepository<ExchangeReservation, Long> {
    @Query(
        """select coalesce(sum(r.fromAmount), 0) from ExchangeReservation r
           where r.userId = :userId and r.fromCurrencyId = :currencyId and r.status = :status"""
    )
    fun sumActiveFunds(userId: Long, currencyId: Long, status: ReservationStatus = ReservationStatus.ACTIVE): BigDecimal
}
