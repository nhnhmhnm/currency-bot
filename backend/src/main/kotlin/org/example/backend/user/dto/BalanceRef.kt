package org.example.backend.user.dto

import org.example.backend.enums.BalanceOwnerType

data class BalanceRef(
    val type: BalanceOwnerType,
    val id: Long?
) {
    companion object {
        fun userWallet(userId: Long) = BalanceRef(BalanceOwnerType.USER_WALLET, userId)
        fun companyAccount(accountId: Long) = BalanceRef(BalanceOwnerType.COMPANY_ACCOUNT, accountId)
        fun bankExternal() = BalanceRef(BalanceOwnerType.BANK_EXTERNAL, null)
    }
}
