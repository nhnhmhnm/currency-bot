package org.example.backend.finance.repository

import org.example.backend.finance.domain.Currency
import org.springframework.data.jpa.repository.JpaRepository

interface CurrencyRepository : JpaRepository<Currency, Long> {
    fun findByCode(code: String): Currency
}
