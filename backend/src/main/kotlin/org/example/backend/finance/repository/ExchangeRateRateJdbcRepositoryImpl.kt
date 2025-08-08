package org.example.backend.finance.repository

import org.example.backend.common.util.toDTO
import org.example.backend.enums.ExchangeType
import org.example.backend.exchange.dto.ExchangeDTO
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository

@Repository
class ExchangeRateRateJdbcRepositoryImpl(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : ExchangeRateJdbcRepository {

    override fun findBestBuyRate(currencyCode: String): ExchangeDTO? {
        val sql = """
            SELECT bank_id, currency_id, buy_rate AS best_rate FROM exchange_rate er
            JOIN currency c ON er.currency_id = c.id
            WHERE c.code = :currencyCode
                AND er.notice_time BETWEEN (
                    SELECT MAX(er2.notice_time) - INTERVAL 5 MINUTE FROM exchange_rate er2
                    JOIN currency c2 ON er2.currency_id = c2.id
                    WHERE c2.code = :currencyCode
                )
                AND (
                    SELECT MAX(er2.notice_time) FROM exchange_rate er2
                    JOIN currency c2 ON er2.currency_id = c2.id
                    WHERE c2.code = :currencyCode
                )
                AND er.buy_rate IS NOT NULL
            ORDER BY er.buy_rate ASC
            LIMIT 1
        """.trimIndent()

        val params = MapSqlParameterSource("currencyCode", currencyCode)

        return jdbcTemplate.query(sql, params) { rs, _ -> toDTO(rs, ExchangeType.BUY)
        }.firstOrNull()
    }

    override fun findBestSellRate(currencyCode: String): ExchangeDTO? {
        val sql = """
            SELECT bank_id, currency_id, sell_rate AS best_rate FROM exchange_rate er
            JOIN currency c ON er.currency_id = c.id
            WHERE c.code = :currencyCode
                AND er.notice_time BETWEEN (
                    SELECT MAX(er2.notice_time) - INTERVAL 5 MINUTE FROM exchange_rate er2
                    JOIN currency c2 ON er2.currency_id = c2.id
                    WHERE c2.code = :currencyCode
                )
                AND (
                    SELECT MAX(er2.notice_time) FROM exchange_rate er2
                    JOIN currency c2 ON er2.currency_id = c2.id
                    WHERE c2.code = :currencyCode
                )
                AND er.sell_rate IS NOT NULL
            ORDER BY er.sell_rate DESC
            LIMIT 1
        """.trimIndent()

        val params = MapSqlParameterSource("currencyCode", currencyCode)

        return jdbcTemplate.query(sql, params) { rs, _ -> toDTO(rs, ExchangeType.BUY)
        }.firstOrNull()
    }

    override fun findBestBuyBaseRate(currencyCode: String): ExchangeDTO? {
        val sql = """
            SELECT bank_id, currency_id, base_rate AS best_rate FROM exchange_rate er
            JOIN currency c ON er.currency_id = c.id
            WHERE c.code = :currencyCode
                AND er.notice_time BETWEEN (
                    SELECT MAX(er2.notice_time) - INTERVAL 5 MINUTE FROM exchange_rate er2
                    JOIN currency c2 ON er2.currency_id = c2.id
                    WHERE c2.code = :currencyCode
                )
                AND (
                    SELECT MAX(er2.notice_time) FROM exchange_rate er2
                    JOIN currency c2 ON er2.currency_id = c2.id
                    WHERE c2.code = :currencyCode
                )
            ORDER BY er.base_rate ASC
            LIMIT 1
        """.trimIndent()

        val params = MapSqlParameterSource("currencyCode", currencyCode)

        return jdbcTemplate.query(sql, params) { rs, _ -> toDTO(rs, ExchangeType.BUY)
        }.firstOrNull()
    }

    override fun findBestSellBaseRate(currencyCode: String): ExchangeDTO? {
        val sql = """
            SELECT bank_id, currency_id, base_rate AS best_rate FROM exchange_rate er
            JOIN currency c ON er.currency_id = c.id
            WHERE c.code = :currencyCode
                AND er.notice_time BETWEEN (
                    SELECT MAX(er2.notice_time) - INTERVAL 5 MINUTE FROM exchange_rate er2
                    JOIN currency c2 ON er2.currency_id = c2.id
                    WHERE c2.code = :currencyCode
                )
                AND (
                    SELECT MAX(er2.notice_time) FROM exchange_rate er2
                    JOIN currency c2 ON er2.currency_id = c2.id
                    WHERE c2.code = :currencyCode
                )
            ORDER BY er.base_rate DESC
            LIMIT 1
        """.trimIndent()

        val params = MapSqlParameterSource("currencyCode", currencyCode)

        return jdbcTemplate.query(sql, params) { rs, _ -> toDTO(rs, ExchangeType.BUY)
        }.firstOrNull()
    }
}
