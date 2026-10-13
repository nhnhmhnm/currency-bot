package org.example.backend.exchange.repository

import jakarta.persistence.LockModeType
import org.example.backend.exchange.domain.ExchangeOrder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.example.backend.enums.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

interface ExchangeOrderRepository : JpaRepository<ExchangeOrder, Long> {
    companion object {
        val RESERVING_STATUSES = listOf(
            OrderStatus.REQUESTED,
            OrderStatus.QUEUED,
            OrderStatus.PROCESSING,
            OrderStatus.PENDING
        )
    }
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ExchangeOrder o where o.id = :id")
    fun findByIdForUpdate(id: Long): ExchangeOrder?

    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): ExchangeOrder?

    @Query(
        """
        select coalesce(sum(o.fromAmount), 0)
        from ExchangeOrder o
        where o.userId = :userId
          and o.fromCurrencyId = :currencyId
          and o.status in :statuses
        """
    )
    fun sumReservedFromAmount(
        @Param("userId") userId: Long,
        @Param("currencyId") currencyId: Long,
        @Param("statuses") statuses: Collection<OrderStatus>
    ): BigDecimal

    @Query(
        """
        select coalesce(sum(o.usdEquivalentAmount), 0)
        from ExchangeOrder o
        where o.userId = :userId
          and o.requestedAt >= :startAt
          and o.requestedAt < :endAt
          and o.status in :statuses
        """
    )
    fun sumDailyUsdEquivalentAmount(
        @Param("userId") userId: Long,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime,
        @Param("statuses") statuses: Collection<OrderStatus>
    ): BigDecimal
}
