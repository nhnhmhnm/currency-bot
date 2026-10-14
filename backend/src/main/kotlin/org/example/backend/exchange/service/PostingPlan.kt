package org.example.backend.exchange.service

import org.example.backend.enums.CompanyAccount
import org.example.backend.enums.WalletFxHistoryType
import java.math.BigDecimal

data class WalletPosting(
    val currencyId: Long,
    val delta: BigDecimal,
    val historyType: WalletFxHistoryType
)

data class AccountPosting(
    val code: CompanyAccount,
    val delta: BigDecimal
)

data class PostingPlan(
    val orderId: Long,
    val userId: Long,
    val walletPostings: List<WalletPosting>,
    val accountPostings: List<AccountPosting>
) {
    init {
        require(accountPostings.map { it.code }.distinct().size == accountPostings.size)
        val currencyIds = walletPostings.map { it.currencyId }.toSet() +
            accountPostings.map { it.code.currencyId }
        for (currencyId in currencyIds) {
            val walletDelta = walletPostings.filter { it.currencyId == currencyId }
                .fold(BigDecimal.ZERO) { sum, posting -> sum.add(posting.delta) }
            val cashDelta = accountPostings.filter {
                it.code != CompanyAccount.COMMISSION_ACCOUNT && it.code.currencyId == currencyId
            }.fold(BigDecimal.ZERO) { sum, posting -> sum.add(posting.delta) }
            val feeDelta = accountPostings.filter {
                it.code == CompanyAccount.COMMISSION_ACCOUNT && it.code.currencyId == currencyId
            }.fold(BigDecimal.ZERO) { sum, posting -> sum.add(posting.delta) }
            require(cashDelta.compareTo(walletDelta.add(feeDelta)) == 0) {
                "Unbalanced posting plan for currencyId=$currencyId"
            }
        }
    }

    companion object {
        fun buy(
            orderId: Long,
            userId: Long,
            fromCurrencyId: Long,
            toCurrencyId: Long,
            fromAmount: BigDecimal,
            exchangeAmount: BigDecimal,
            toAmount: BigDecimal,
            commissionAmount: BigDecimal
        ) = PostingPlan(
            orderId,
            userId,
            listOfNotNull(
                WalletPosting(fromCurrencyId, exchangeAmount.negate(), WalletFxHistoryType.USER_TO_COMPANY),
                WalletPosting(fromCurrencyId, commissionAmount.negate(), WalletFxHistoryType.USER_TO_COMPANY)
                    .takeIf { commissionAmount > BigDecimal.ZERO },
                WalletPosting(toCurrencyId, toAmount, WalletFxHistoryType.COMPANY_TO_USER)
            ),
            listOfNotNull(
                AccountPosting(CompanyAccount.ofCurrencyId(fromCurrencyId), exchangeAmount.negate()),
                AccountPosting(CompanyAccount.ofCurrencyId(toCurrencyId), toAmount),
                AccountPosting(CompanyAccount.COMMISSION_ACCOUNT, commissionAmount)
                    .takeIf { commissionAmount > BigDecimal.ZERO }
            )
        ).also {
            require(fromAmount.compareTo(exchangeAmount.add(commissionAmount)) == 0)
        }

        fun sell(
            orderId: Long,
            userId: Long,
            fromCurrencyId: Long,
            toCurrencyId: Long,
            fromAmount: BigDecimal,
            rawToAmount: BigDecimal,
            toAmount: BigDecimal,
            commissionAmount: BigDecimal
        ) = PostingPlan(
            orderId,
            userId,
            listOf(
                WalletPosting(fromCurrencyId, fromAmount.negate(), WalletFxHistoryType.USER_TO_COMPANY),
                WalletPosting(toCurrencyId, toAmount, WalletFxHistoryType.COMPANY_TO_USER)
            ),
            listOfNotNull(
                AccountPosting(CompanyAccount.ofCurrencyId(fromCurrencyId), fromAmount.negate()),
                AccountPosting(CompanyAccount.ofCurrencyId(toCurrencyId), rawToAmount),
                AccountPosting(CompanyAccount.COMMISSION_ACCOUNT, commissionAmount)
                    .takeIf { commissionAmount > BigDecimal.ZERO }
            )
        ).also {
            require(rawToAmount.compareTo(toAmount.add(commissionAmount)) == 0)
        }
    }
}
