package org.example.backend.enums

enum class WalletFxHistoryType(
    val code: String,
    val value: String
) {
    USER_TO_COMPANY("USER_TO_COMPANY", "유저 -> 회사"),
    COMPANY_TO_USER("COMPANY_TO_USER", "회사 -> 유저"),
    FX_BUY("FX_BUY", "외화 구매"),
    FX_SELL("FX_SELL", "외화 판매"),
    COMMISSION("COMMISSION", "수수료"),
    PROFIT("PROFIT", "차익")
}