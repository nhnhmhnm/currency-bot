package org.example.backend.finance.repository

import org.example.backend.finance.domain.ExchangeRate
import org.springframework.data.jpa.repository.JpaRepository

interface ExchangeRateRepository : JpaRepository<ExchangeRate, Long> {
    fun findRecentRateByCurrencyId(currencyId: Long): List<ExchangeRate>
}
