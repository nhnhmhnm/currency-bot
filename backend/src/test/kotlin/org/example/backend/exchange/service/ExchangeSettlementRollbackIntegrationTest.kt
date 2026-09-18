package org.example.backend.exchange.service

import io.kotest.matchers.shouldBe
import org.example.backend.enums.ExchangeType
import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.dto.BestExchangeRateDto
import org.example.backend.exchange.dto.ExchangeLedgerCommand
import org.example.backend.exchange.dto.ExchangeLedgerResponse
import org.example.backend.exchange.repository.ExchangeLedgerRepository
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.exchange.repository.TransactionRepository
import org.example.backend.finance.domain.Currency
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.WalletFxHistoryRepository
import org.example.backend.user.repository.WalletRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:settlement-rollback;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.scheduling.enabled=false"
    ],
    classes = [
        org.example.backend.BackendApplication::class,
        ExchangeSettlementRollbackIntegrationTest.TestConfig::class
    ]
)
class ExchangeSettlementRollbackIntegrationTest {
    @Autowired
    private lateinit var exchangeOrderService: ExchangeOrderService

    @Autowired
    private lateinit var exchangeOrderRepository: ExchangeOrderRepository

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var exchangeLedgerRepository: ExchangeLedgerRepository

    @Autowired
    private lateinit var walletFxHistoryRepository: WalletFxHistoryRepository

    @Autowired
    private lateinit var ledgerService: ControllableLedgerService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        ledgerService.failOnRecord = false
        walletFxHistoryRepository.deleteAll()
        exchangeLedgerRepository.deleteAll()
        transactionRepository.deleteAll()
        exchangeOrderRepository.deleteAll()
        walletRepository.deleteAll()
        accountRepository.deleteAll()
        jdbcTemplate.update("delete from \"user\"")
        jdbcTemplate.update("delete from currency")

        jdbcTemplate.update(
            "insert into currency (id, code, name, scale, unit) values (?, ?, ?, ?, ?)",
            KRW_ID,
            "KRW",
            "Korean Won",
            0,
            BigDecimal.ONE
        )
        jdbcTemplate.update(
            "insert into currency (id, code, name, scale, unit) values (?, ?, ?, ?, ?)",
            USD_ID,
            "USD",
            "US Dollar",
            2,
            BigDecimal.ONE
        )
        jdbcTemplate.update(
            "insert into \"user\" (id, device, password, name, phone, type, is_active, created_at) " +
                "values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
            COMPANY_USER_ID,
            "company-device",
            "password",
            "company",
            "01000000001",
            "SUPER",
            true
        )
        jdbcTemplate.update(
            "insert into \"user\" (id, device, password, name, phone, type, is_active, created_at) " +
                "values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
            USER_ID,
            "user-device",
            "password",
            "user",
            "01000000010",
            "GENERAL",
            true
        )

        jdbcTemplate.update(
            "insert into wallet (user_id, currency_id, balance, is_connected) values (?, ?, ?, ?)",
            USER_ID,
            KRW_ID,
            USER_KRW_BALANCE,
            false
        )
        jdbcTemplate.update(
            "insert into wallet (user_id, currency_id, balance, is_connected) values (?, ?, ?, ?)",
            USER_ID,
            USD_ID,
            USER_USD_BALANCE,
            false
        )
        insertCompanyAccount(KRW_ID, "222222-22-222222", COMPANY_KRW_BALANCE)
        insertCompanyAccount(USD_ID, "444444-44-444444", COMPANY_USD_BALANCE)
        insertCompanyAccount(KRW_ID, "111111-11-111111", COMMISSION_BALANCE)
    }

    @Test
    fun `ledger failure rolls back balances transactions ledger and wallet history`() {
        val order = exchangeOrderRepository.save(
            ExchangeOrder(
                userId = USER_ID,
                bankId = 0L,
                fromCurrencyId = KRW_ID,
                toCurrencyId = USD_ID,
                fromAmount = ORDER_AMOUNT,
                toAmount = BigDecimal.ZERO,
                exchangeRate = BigDecimal.ZERO,
                usdEquivalentAmount = BigDecimal("76.923077"),
                orderType = OrderType.BUY,
                status = OrderStatus.QUEUED
            )
        )
        ledgerService.failOnRecord = true

        assertThrows<IllegalStateException> {
            exchangeOrderService.processQueuedOrder(order.id!!)
        }

        walletRepository.findByUserIdAndCurrencyId(USER_ID, KRW_ID)!!.balance shouldBe USER_KRW_BALANCE
        walletRepository.findByUserIdAndCurrencyId(USER_ID, USD_ID)!!.balance shouldBe USER_USD_BALANCE
        companyBalance(KRW_ID, "222222-22-222222") shouldBe COMPANY_KRW_BALANCE
        companyBalance(USD_ID, "444444-44-444444") shouldBe COMPANY_USD_BALANCE
        companyBalance(KRW_ID, "111111-11-111111") shouldBe COMMISSION_BALANCE
        transactionRepository.count() shouldBe 0L
        exchangeLedgerRepository.count() shouldBe 0L
        walletFxHistoryRepository.count() shouldBe 0L
        exchangeOrderRepository.findById(order.id!!).orElseThrow().status shouldBe OrderStatus.QUEUED
    }

    @Test
    fun `concurrent orders never overdraw and every settlement has complete records`() {
        val requests = runConcurrently(11) { index ->
            exchangeOrderService.requestBuyOrder(
                userId = USER_ID,
                currencyCode = "USD",
                fromAmount = ORDER_AMOUNT,
                idempotencyKey = "balance-$index"
            )
        }

        val acceptedOrders = requests.mapNotNull { it.getOrNull() }
        val rejectedOrders = requests.mapNotNull { it.exceptionOrNull() }
        acceptedOrders.size shouldBe 10
        rejectedOrders.size shouldBe 1
        (rejectedOrders.single() as UserException).errorCode shouldBe ErrorCode.INSUFFICIENT_WALLET_BALANCE.code

        val settlements = runConcurrently(acceptedOrders.size) { index ->
            exchangeOrderService.processQueuedOrder(acceptedOrders[index].id)
        }
        settlements.count { it.isSuccess } shouldBe 10

        walletRepository.findByUserIdAndCurrencyId(USER_ID, KRW_ID)!!.balance shouldBe BigDecimal("0.00")
        walletRepository.findByUserIdAndCurrencyId(USER_ID, USD_ID)!!.balance shouldBe BigDecimal("869.20")
        exchangeOrderRepository.findAll().count { it.status == OrderStatus.SETTLED } shouldBe 10
        transactionRepository.count() shouldBe 20L
        exchangeLedgerRepository.count() shouldBe 10L
        walletFxHistoryRepository.count() shouldBe 30L
    }

    @Test
    fun `concurrent requests cannot bypass the daily 4000 USD limit`() {
        jdbcTemplate.update(
            "update wallet set balance = ? where user_id = ? and currency_id = ?",
            BigDecimal("10000000.00"),
            USER_ID,
            KRW_ID
        )
        val oneMillion = BigDecimal("1000000")

        val requests = runConcurrently(6) { index ->
            exchangeOrderService.requestBuyOrder(
                userId = USER_ID,
                currencyCode = "USD",
                fromAmount = oneMillion,
                idempotencyKey = "daily-limit-$index"
            )
        }

        requests.count { it.isSuccess } shouldBe 5
        val failure = requests.single { it.isFailure }.exceptionOrNull() as UserException
        failure.errorCode shouldBe ErrorCode.DAILY_EXCHANGE_LIMIT_EXCEEDED.code
        exchangeOrderRepository.count() shouldBe 5L
    }

    private fun <T> runConcurrently(taskCount: Int, action: (Int) -> T): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(taskCount)
        val ready = CountDownLatch(taskCount)
        val start = CountDownLatch(1)

        return try {
            val futures = (0 until taskCount).map { index ->
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS)) { "concurrent test did not start in time" }
                    runCatching { action(index) }
                })
            }
            check(ready.await(5, TimeUnit.SECONDS)) { "workers were not ready in time" }
            start.countDown()
            futures.map { it.get(20, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun insertCompanyAccount(currencyId: Long, accountNum: String, balance: BigDecimal) {
        jdbcTemplate.update(
            "insert into account (user_id, bank_id, currency_id, account_num, balance, is_active, created_at) " +
                "values (?, ?, ?, ?, ?, ?, current_timestamp)",
            COMPANY_USER_ID,
            1L,
            currencyId,
            accountNum,
            balance,
            true
        )
    }

    private fun companyBalance(currencyId: Long, accountNum: String): BigDecimal {
        return accountRepository.findByCurrencyIdAndAccountNum(currencyId, accountNum)!!.balance
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun fixedExchangeService(): ExchangeService = FixedExchangeService()

        @Bean
        @Primary
        fun controllableLedgerService(
            exchangeLedgerRepository: ExchangeLedgerRepository
        ): ControllableLedgerService = ControllableLedgerService(exchangeLedgerRepository)
    }

    class FixedExchangeService : ExchangeService {
        override fun getBestBuyRate(currencyCode: String) = bestRate(ExchangeType.BUY)
        override fun getBestSellRate(currencyCode: String) = bestRate(ExchangeType.SELL)
        override fun getBestBuyBaseRate(currencyCode: String) = bestRate(ExchangeType.BUY)
        override fun getBestSellBaseRate(currencyCode: String) = bestRate(ExchangeType.SELL)

        override fun calculateExchange(
            fromCurrency: Currency,
            toCurrency: Currency,
            exchangeRate: BigDecimal,
            fromAmount: BigDecimal
        ): Pair<BigDecimal, BigDecimal> {
            val rawAmount = if (fromCurrency.code == "KRW") {
                fromAmount.divide(exchangeRate, 10, RoundingMode.DOWN)
            } else {
                fromAmount.multiply(exchangeRate)
            }
            val amount = rawAmount.setScale(toCurrency.scale, RoundingMode.DOWN)
            return amount to rawAmount.subtract(amount)
        }

        private fun bestRate(type: ExchangeType) = BestExchangeRateDto(
            bankId = 1L,
            currencyId = USD_ID,
            bestRate = RATE,
            type = type
        )
    }

    class ControllableLedgerService(
        private val exchangeLedgerRepository: ExchangeLedgerRepository
    ) : ExchangeLedgerService {
        var failOnRecord: Boolean = false

        override fun record(ledger: ExchangeLedgerCommand): ExchangeLedgerResponse {
            if (failOnRecord) {
                throw IllegalStateException("forced ledger failure")
            }
            return ExchangeLedgerServiceImpl(exchangeLedgerRepository).record(ledger)
        }
    }

    companion object {
        private const val COMPANY_USER_ID = 1L
        private const val USER_ID = 10L
        private const val KRW_ID = 1L
        private const val USD_ID = 2L
        private val RATE = BigDecimal("1300")
        private val ORDER_AMOUNT = BigDecimal("100000")
        private val USER_KRW_BALANCE = BigDecimal("1000000.00")
        private val USER_USD_BALANCE = BigDecimal("100.00")
        private val COMPANY_KRW_BALANCE = BigDecimal("1000000000.00")
        private val COMPANY_USD_BALANCE = BigDecimal("1000000.00")
        private val COMMISSION_BALANCE = BigDecimal("0.00")
    }
}
