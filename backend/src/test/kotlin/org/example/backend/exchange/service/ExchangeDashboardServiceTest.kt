package org.example.backend.exchange.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.example.backend.exchange.repository.ExchangeReservationRepository
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.ExchangeRateJdbcRepository
import org.example.backend.exchange.dto.BestExchangeRateDto
import org.example.backend.enums.ExchangeType
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
    private val reservations = mockk<ExchangeReservationRepository>()
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
        service = ExchangeDashboardService(jdbc, reservations, orders, mockk<CurrencyRepository>(), mockk<ExchangeService>())
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
        every { orders.findOccupiedArbitrageSlots(7, any()) } returns listOf(1, 3)
        every { reservations.sumActiveFunds(7, 1, any()) } returns BigDecimal("40")
        val result = service.summary(7)
        assertEquals(2, result.arbitrageRemainingCount)
        assertEquals(1, result.wallets.size)
        assertEquals(0, result.wallets.first().available.compareTo(BigDecimal("60")))
        verify(exactly = 1) { orders.findOccupiedArbitrageSlots(7, any()) }
    }

    @Test
    fun `JPY arbitrage terms use fixed KRW amount and net profit without USD quote`() {
        val currencies = mockk<CurrencyRepository>()
        val exchange = mockk<ExchangeService>()
        val calculator = ExchangeServiceImpl(mockk<ExchangeRateJdbcRepository>())
        val krw = Currency(3L, "KRW", "Korean Won", 0, BigDecimal.ONE)
        val jpy = Currency(2L, "JPY", "Japanese Yen", 0, BigDecimal("100"))
        every { currencies.findByCode("KRW") } returns krw
        every { currencies.findByCode("JPY") } returns jpy
        every { exchange.getBestBuyBaseRate("JPY") } returns BestExchangeRateDto(1L, jpy.id, BigDecimal("900"), ExchangeType.BUY)
        every { exchange.getBestSellBaseRate("JPY") } returns BestExchangeRateDto(1L, jpy.id, BigDecimal("910"), ExchangeType.SELL)
        every { exchange.calculateExchange(any(), any(), any(), any()) } answers {
            calculator.calculateExchange(firstArg(), secondArg(), thirdArg(), arg(3))
        }

        val terms = ExchangeDashboardService(jdbc, reservations, orders, currencies, exchange).arbitrageTerms("JPY")
        assertEquals(BigDecimal("1000000"), terms.amountKrw)
        assertEquals(BigDecimal("11079"), terms.estimatedProfitKrw)
        verify(exactly = 0) { currencies.findByCode("USD") }
    }
}
