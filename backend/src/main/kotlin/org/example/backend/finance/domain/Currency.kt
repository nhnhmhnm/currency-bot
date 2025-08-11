package org.example.backend.finance.domain

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "currency")
class Currency(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column(name = "code", nullable = false, unique = true)
    val code: String,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "scale", nullable = false)
    val scale: Int,

    @Column(name = "unit", nullable = false)
    val unit: BigDecimal
)
