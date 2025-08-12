package org.example.backend.finance.repository

import org.example.backend.finance.domain.Bank
import org.springframework.data.jpa.repository.JpaRepository

interface BankRepository : JpaRepository<Bank, Long> {
}
