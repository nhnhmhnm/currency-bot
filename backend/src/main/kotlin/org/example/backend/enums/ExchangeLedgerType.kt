package org.example.backend.enums

enum class ExchangeLedgerType(
        val code: String,
        val value: String
) {
    DEPOSIT("DEPOSIT", "입금"),
    WITHDRAWAL("WITHDRAWAL", "출금"),
    FX_BUY("FX_BUY", "외화 구매"),
    FX_SELL("FX_SELL", "외화 판매"),
    COMMISSION("COMMISSION", "수수료"),
    OTHER("OTHER", "기타")
}
