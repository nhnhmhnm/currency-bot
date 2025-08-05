package org.example.backend.finance.domain

import jakarta.persistence.*

@Entity
@Table(name = "bank")
class Bank(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    val code: String,

    @Column(nullable = false)
    val name: String
)
