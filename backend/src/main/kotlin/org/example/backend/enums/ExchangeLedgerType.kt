package org.example.backend.enums

enum class ExchangeLedgerType(
        val code: String,
        val value: String
) {
    DEPOSIT("DEPOSIT", "입금"),
    WITHDRAWAL("WITHDRAWAL", "출금"),
    COMMISSION("COMMISSION", "수수료"),
    OTHER("OTHER", "기타")
}
