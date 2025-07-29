package org.example.backend.finance.repository

import org.example.backend.finance.domain.ExchangeLedger
import org.springframework.data.jpa.repository.JpaRepository

interface ExchangeLedgerRepository : JpaRepository<ExchangeLedger, Long>
