package org.example.backend.exchange.repository

import org.example.backend.exchange.domain.ExchangeOrder
import org.springframework.data.jpa.repository.JpaRepository

interface ExchangeOrderRepository : JpaRepository<ExchangeOrder, Long> {
    fun findByUserId(userId: Long): List<ExchangeOrder>
}
