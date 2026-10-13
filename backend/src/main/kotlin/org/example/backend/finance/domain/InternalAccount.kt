package org.example.backend.finance.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.example.backend.enums.CompanyAccount
import java.math.BigDecimal

@Entity
@Table(name = "internal_account")
class InternalAccount(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 40)
    val code: CompanyAccount,

    @Column(name = "currency_id", nullable = false)
    val currencyId: Long,

    @Column(name = "balance", nullable = false, precision = 24, scale = 8)
    var balance: BigDecimal = BigDecimal.ZERO
)
