package org.example.backend.finance.domain

import jakarta.persistence.*

@Entity
@Table(name = "currency")
class Currency(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "code", nullable = false, unique = true)
    val code: String,

    @Column(name = "name", nullable = false)
    val name: String
)
