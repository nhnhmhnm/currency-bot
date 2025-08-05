package org.example.backend.enums

enum class WalletTransactionType(
  val code: String,
  val value: String
) {
  DEPOSIT("DEPOSIT", "입금"),
  WITHDRAWAL("WITHDRAWAL", "출금")
}