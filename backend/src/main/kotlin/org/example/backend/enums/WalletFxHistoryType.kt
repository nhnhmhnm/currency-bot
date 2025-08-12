package org.example.backend.enums

enum class WalletFxHistoryType(
    val code: String,
    val value: String
) {
    FX_BUY("FX_BUY", "외화 구매"),
    FX_SELL("FX_SELL", "외화 판매")
}
