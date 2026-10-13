package org.example.backend.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "simulated_bank_balance")
class SimulatedBankBalance(
    @Id
    @Column(name = "account_id")
    val accountId: Long,

    @Column(name = "balance", nullable = false, precision = 24, scale = 8)
    var balance: BigDecimal = BigDecimal.ZERO
)
