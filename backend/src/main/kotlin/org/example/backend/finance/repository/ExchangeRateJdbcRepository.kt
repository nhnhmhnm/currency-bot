package org.example.backend.finance.repository

import org.example.backend.finance.domain.ExchangeRate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDateTime

@Repository
class ExchangeRateJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        ExchangeRate(
            id = rs.getLong("id"),
            bankId = rs.getLong("bank_id"),
            currencyId = rs.getLong("currency_id"),
            baseRate = rs.getBigDecimal("base_rate"),
            buyRate = rs.getBigDecimal("buy_rate"),
            sellRate = rs.getBigDecimal("sell_rate"),
            noticeTime = rs.getTimestamp("notice_time").toLocalDateTime(),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime()
        )
    }

    fun findLatestRatesPerBankByCurrencyCode(currencyCode: String): List<ExchangeRate> {
        val sql = """
            SELECT r.*
            FROM exchange_rate r
            JOIN (
                SELECT bank_id, MAX(notice_time) AS latest_time
                FROM exchange_rate e
                JOIN currency c ON e.currency_id = c.id
                WHERE c.code = :currencyCode
                GROUP BY bank_id
            ) latest
            ON r.bank_id = latest.bank_id AND r.notice_time = latest.latest_time
            JOIN currency cur ON r.currency_id = cur.id
            WHERE cur.code = :currencyCode
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("currencyCode", currencyCode)

        return jdbcTemplate.query(sql, params, rowMapper)
    }
}
