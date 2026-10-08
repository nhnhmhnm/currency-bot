package org.example.backend.exchange.service

import org.example.backend.enums.OrderStatus
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class RatePoint(
    val bankId: Long,
    val bankName: String,
    val time: LocalDateTime,
    val buyRate: BigDecimal?,
    val sellRate: BigDecimal?,
    val baseRate: BigDecimal?
)

data class DailyRates(
    val date: LocalDate,
    val currency: String,
    val unit: BigDecimal,
    val scale: Int,
    val points: List<RatePoint>
)

data class WalletBalance(val currency: String, val balance: BigDecimal, val available: BigDecimal)
data class ExchangeSummary(
    val usedUsd: BigDecimal,
    val limitUsd: BigDecimal,
    val remainingUsd: BigDecimal,
    val wallets: List<WalletBalance>
)

@Service
@Transactional(readOnly = true)
class ExchangeDashboardService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val validator: ExchangeOrderRequestValidator,
    private val orders: ExchangeOrderRepository
) {
    fun rates(currency: String): DailyRates {
        if (!currency.matches(Regex("[A-Z]{3}"))) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid currency")
        }
        val metadata = jdbc.query(
            "SELECT unit, scale FROM currency WHERE code = :currency",
            mapOf("currency" to currency)
        ) { rs, _ -> rs.getBigDecimal("unit") to rs.getInt("scale") }.firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Currency not found")
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val points = jdbc.query(
            """
            SELECT er.bank_id, b.name AS bank_name, er.notice_time,
                   er.buy_rate, er.sell_rate, er.base_rate
            FROM exchange_rate er
            JOIN currency c ON c.id = er.currency_id
            JOIN bank b ON b.id = er.bank_id
            WHERE c.code = :currency AND er.notice_time >= :start AND er.notice_time < :end
            ORDER BY er.notice_time, er.id
            """.trimIndent(),
            mapOf("currency" to currency, "start" to today.atStartOfDay(), "end" to today.plusDays(1).atStartOfDay())
        ) { rs, _ ->
            RatePoint(
                rs.getLong("bank_id"), rs.getString("bank_name"), rs.getTimestamp("notice_time").toLocalDateTime(),
                rs.getBigDecimal("buy_rate"), rs.getBigDecimal("sell_rate"), rs.getBigDecimal("base_rate")
            )
        }
        return DailyRates(today, currency, metadata.first, metadata.second, points)
    }

    fun summary(userId: Long): ExchangeSummary {
        val used = validator.dailyUsedUsd(userId)
        val limit = ExchangeOrderRequestValidator.DAILY_LIMIT_USD
        val reserving = listOf(OrderStatus.REQUESTED, OrderStatus.QUEUED, OrderStatus.PROCESSING, OrderStatus.PENDING)
        val wallets = jdbc.query(
            "SELECT w.currency_id, c.code, w.balance FROM wallet w JOIN currency c ON c.id = w.currency_id WHERE w.user_id = :userId ORDER BY c.code",
            mapOf("userId" to userId)
        ) { rs, _ ->
            val balance = rs.getBigDecimal("balance")
            val reserved = orders.sumReservedFromAmount(userId, rs.getLong("currency_id"), reserving)
            WalletBalance(rs.getString("code"), balance, balance.subtract(reserved).max(BigDecimal.ZERO))
        }
        return ExchangeSummary(used, limit, limit.subtract(used).max(BigDecimal.ZERO), wallets)
    }
}
