package org.example.backend.exchange.repository

import jakarta.persistence.LockModeType
import org.example.backend.exchange.domain.ExchangeOrder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ExchangeOrderRepository : JpaRepository<ExchangeOrder, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ExchangeOrder o where o.id = :id")
    fun findByIdForUpdate(id: Long): ExchangeOrder?

    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): ExchangeOrder?
}
