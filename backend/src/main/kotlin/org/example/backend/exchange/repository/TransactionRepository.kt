package org.example.backend.exchange.repository

import org.example.backend.exchange.domain.Transaction
import org.springframework.data.jpa.repository.JpaRepository

interface TransactionRepository : JpaRepository<Transaction, Long> {}
