package org.example.backend.exchange.service

import org.example.backend.exchange.repository.ExchangeReservationRepository
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.finance.repository.CurrencyRepository
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
    val arbitrageUsedCount: Int,
    val arbitrageLimitCount: Int,
    val arbitrageRemainingCount: Int,
    val wallets: List<WalletBalance>
)
data class ArbitrageTerms(
    val currency: String,
    val amountKrw: BigDecimal,
    val buyRate: BigDecimal,
    val sellRate: BigDecimal,
    val foreignAmount: BigDecimal,
    val feeKrw: BigDecimal,
    val estimatedReceiveKrw: BigDecimal,
    val estimatedProfitKrw: BigDecimal
)

@Service
@Transactional(readOnly = true)
class ExchangeDashboardService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val reservations: ExchangeReservationRepository,
    private val orders: ExchangeOrderRepository,
    private val currencies: CurrencyRepository,
    private val exchangeService: ExchangeService
) {
    fun arbitrageTerms(currencyCode: String): ArbitrageTerms {
        val krw = currencies.findByCode("KRW")
        val foreign = currencies.findByCode(currencyCode)
        val buyRate = exchangeService.getBestBuyBaseRate(currencyCode).bestRate
        val sellRate = exchangeService.getBestSellBaseRate(currencyCode).bestRate
        val estimate = ArbitrageQuotePolicy.estimate(krw, foreign, buyRate, sellRate, exchangeService)
        return ArbitrageTerms(
            currencyCode,
            ArbitrageQuotePolicy.AMOUNT_KRW,
            buyRate,
            sellRate,
            estimate.foreignAmount,
            estimate.feeKrw,
            estimate.receiveKrw,
            estimate.profitKrw
        )
    }
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
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val used = orders.findOccupiedArbitrageSlots(userId, today).size
        val limit = ArbitrageQuotePolicy.DAILY_LIMIT
        val wallets = jdbc.query(
            "SELECT w.currency_id, c.code, w.balance FROM wallet w JOIN currency c ON c.id = w.currency_id WHERE w.user_id = :userId ORDER BY c.code",
            mapOf("userId" to userId)
        ) { rs, _ ->
            val balance = rs.getBigDecimal("balance")
            val reserved = reservations.sumActiveFunds(userId, rs.getLong("currency_id"))
            WalletBalance(rs.getString("code"), balance, balance.subtract(reserved).max(BigDecimal.ZERO))
        }
        return ExchangeSummary(used, limit, (limit - used).coerceAtLeast(0), wallets)
    }
}
