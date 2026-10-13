package org.example.backend.enums

enum class CompanyAccount(val currencyId: Long) {
    COMMISSION_ACCOUNT(1),
    KRW_ACCOUNT(1),
    USD_ACCOUNT(2),
    JPY_ACCOUNT(3);

    companion object {
        private val byCurrencyId = entries.filter { it != COMMISSION_ACCOUNT }.associateBy { it.currencyId }
        fun ofCurrencyId(id: Long) =
            byCurrencyId[id] ?: error("회사 계좌 없음: currencyId=$id")
    }
}
