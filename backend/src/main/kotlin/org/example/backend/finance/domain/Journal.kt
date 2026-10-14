package org.example.backend.finance.domain

import jakarta.persistence.*
import org.hibernate.annotations.Immutable
import java.math.BigDecimal
import java.time.LocalDateTime

enum class JournalSource { EXCHANGE_ORDER, WALLET_TRANSFER }
enum class LedgerAccountType { COMPANY_CASH, CUSTOMER_LIABILITY, COMMISSION_REVENUE }

@Entity
@Immutable
@Table(name = "journal", uniqueConstraints = [UniqueConstraint(name = "uk_journal_source", columnNames = ["source_type", "source_id"])])
class Journal(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    val sourceType: JournalSource,
    @Column(name = "source_id", nullable = false)
    val sourceId: Long,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Immutable
@Table(
    name = "journal_line",
    uniqueConstraints = [UniqueConstraint(name = "uk_journal_line_number", columnNames = ["journal_id", "line_number"])],
    indexes = [Index(name = "idx_journal_line_account", columnList = "account_type,account_id,id")]
)
class JournalLine(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "journal_id", nullable = false)
    val journalId: Long,
    @Column(name = "line_number", nullable = false)
    val lineNumber: Int,
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    val accountType: LedgerAccountType,
    @Column(name = "account_id", nullable = false)
    val accountId: Long,
    @Column(name = "currency_id", nullable = false)
    val currencyId: Long,
    @Column(name = "debit", nullable = false, precision = 24, scale = 8)
    val debit: BigDecimal,
    @Column(name = "credit", nullable = false, precision = 24, scale = 8)
    val credit: BigDecimal,
    @Column(name = "balance_before", nullable = false, precision = 24, scale = 8)
    val balanceBefore: BigDecimal,
    @Column(name = "balance_after", nullable = false, precision = 24, scale = 8)
    val balanceAfter: BigDecimal
)
