package org.example.backend.exchange.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ExchangeDashboardServiceTest {
    private lateinit var jdbc: NamedParameterJdbcTemplate
    private val validator = mockk<ExchangeOrderRequestValidator>()
    private val orders = mockk<ExchangeOrderRepository>()
    private lateinit var service: ExchangeDashboardService

    @BeforeEach
    fun setup() {
        jdbc = NamedParameterJdbcTemplate(DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "sa", ""))
        jdbc.jdbcTemplate.execute("CREATE TABLE currency (id BIGINT, code VARCHAR(3), unit DECIMAL(19,2), scale INT)")
        jdbc.jdbcTemplate.execute("CREATE TABLE bank (id BIGINT, name VARCHAR(50))")
        jdbc.jdbcTemplate.execute("CREATE TABLE exchange_rate (id BIGINT, bank_id BIGINT, currency_id BIGINT, notice_time TIMESTAMP, buy_rate DECIMAL(19,2), sell_rate DECIMAL(19,2), base_rate DECIMAL(19,2))")
        jdbc.jdbcTemplate.execute("CREATE TABLE wallet (user_id BIGINT, currency_id BIGINT, balance DECIMAL(19,2))")
        jdbc.jdbcTemplate.update("INSERT INTO currency VALUES (1, 'USD', 1, 2), (2, 'JPY', 100, 0)")
        jdbc.jdbcTemplate.update("INSERT INTO bank VALUES (1, 'Bank A')")
        service = ExchangeDashboardService(jdbc, validator, orders)
    }

    @Test
    fun `rates include only the selected currency and Seoul business day`() {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        listOf(today.minusDays(1).atTime(23, 59), today.atTime(9, 0), today.plusDays(1).atStartOfDay()).forEachIndexed { i, time ->
            jdbc.update("INSERT INTO exchange_rate VALUES (:id, 1, 1, :time, 1400, 1380, 1390)", mapOf("id" to i, "time" to time))
        }
        val result = service.rates("USD")
        assertEquals(1, result.points.size)
        assertEquals(today.atTime(9, 0), result.points.first().time)
        assertEquals(BigDecimal("1400.00"), result.points.first().buyRate)
        assertEquals(0, service.rates("JPY").points.size)
        assertEquals(BigDecimal("100.00"), service.rates("JPY").unit)
        assertThrows(ResponseStatusException::class.java) { service.rates("USD' OR 1=1") }
        assertThrows(ResponseStatusException::class.java) { service.rates("EUR") }
    }

    @Test
    fun `summary uses server quota and only the authenticated users wallets`() {
        jdbc.jdbcTemplate.update("INSERT INTO wallet VALUES (7, 1, 100), (8, 1, 900)")
        every { validator.dailyUsedUsd(7) } returns BigDecimal("1250")
        every { orders.sumReservedFromAmount(7, 1, any()) } returns BigDecimal("40")
        val result = service.summary(7)
        assertEquals(0, result.remainingUsd.compareTo(BigDecimal("2750")))
        assertEquals(1, result.wallets.size)
        assertEquals(0, result.wallets.first().available.compareTo(BigDecimal("60")))
        verify(exactly = 1) { validator.dailyUsedUsd(7) }
    }
}
