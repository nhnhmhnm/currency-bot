package org.example.backend.exchange.repository

import org.example.backend.exchange.domain.CompletedOrderView
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CompletedOrderViewRepository : JpaRepository<CompletedOrderView, Long> {
    fun findByUserIdOrderByCompletedAtDesc(userId: Long, pageable: Pageable): List<CompletedOrderView>
}
