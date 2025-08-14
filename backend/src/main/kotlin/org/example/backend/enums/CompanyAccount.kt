package org.example.backend.enums

enum class CompanyAccount(
    val bankId: Long,
    val currencyId: Long,
    val accountNum: String,
) {
    COMMISSION_ACCOUNT(1, 1, "111111-11-111111"),
    KRW_ACCOUNT(1, 1, "222222-22-222222"),
    USD_ACCOUNT(4, 2, "444444-44-444444"),
    JPY_ACCOUNT(7, 3, "777777-77-777777");

    companion object {
        private val byCurrencyId = entries.associateBy { it.currencyId }
        fun ofCurrencyId(id: Long) =
            byCurrencyId[id] ?: error("회사 계좌 없음: currencyId=$id")
    }
}
