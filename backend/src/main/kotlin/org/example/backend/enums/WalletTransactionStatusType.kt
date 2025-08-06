package org.example.backend.enums

enum class WalletTransactionStatusType(
  val code: String,
  val value: String
) {
  PENDING("PENDING", "처리중"),
  COMPLETED("COMPLETED", "입출금 완료"),
  FAILED("FAILED", "입출금 실패")
}