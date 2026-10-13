package org.example.backend.exchange.service

import io.kotest.matchers.shouldBe
import org.example.backend.auth.dto.UserPrincipal
import org.example.backend.enums.ExchangeType
import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.enums.UserType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.dto.BestExchangeRateDto
import org.example.backend.exchange.dto.ExchangeLedgerCommand
import org.example.backend.exchange.dto.ExchangeLedgerResponse
import org.example.backend.exchange.repository.ExchangeLedgerRepository
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.exchange.repository.OutboxEventRepository
import org.example.backend.exchange.repository.TransactionRepository
import org.example.backend.finance.domain.Currency
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.WalletFxHistoryRepository
import org.example.backend.user.repository.WalletRepository
import org.example.backend.user.dto.AccountCreateRequest
import org.example.backend.user.service.AccountService
import org.example.backend.user.service.WalletService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
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
@AutoConfigureMockMvc
class ExchangeSettlementRollbackIntegrationTest {
    @Autowired
    private lateinit var exchangeOrderService: ExchangeOrderService

    @Autowired
    private lateinit var exchangeOrderRepository: ExchangeOrderRepository

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

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

    @Autowired
    private lateinit var walletService: WalletService

    @Autowired
    private lateinit var accountService: AccountService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        ledgerService.failOnRecord = false
        walletFxHistoryRepository.deleteAll()
        jdbcTemplate.update("delete from deposit_withdrawal")
        exchangeLedgerRepository.deleteAll()
        transactionRepository.deleteAll()
        outboxEventRepository.deleteAll()
        exchangeOrderRepository.deleteAll()
        walletRepository.deleteAll()
        accountRepository.deleteAll()
        jdbcTemplate.update("delete from \"user\"")
        jdbcTemplate.update("delete from currency")
        jdbcTemplate.update("delete from bank")

        jdbcTemplate.update("insert into bank (id, code, name) values (?, ?, ?)", 1L, "TEST", "Test bank")

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
            "insert into account (user_id, bank_id, currency_id, account_num, balance, is_active, created_at) " +
                "values (?, ?, ?, ?, ?, ?, current_timestamp)",
            USER_ID,
            1L,
            KRW_ID,
            USER_ACCOUNT_NUM,
            BigDecimal("500000"),
            true
        )
        val userAccountId = accountRepository.findByBankIdAndAccountNum(1L, USER_ACCOUNT_NUM)!!.id!!
        jdbcTemplate.update(
            "update wallet set account_id = ?, is_connected = true where user_id = ? and currency_id = ?",
            userAccountId,
            USER_ID,
            KRW_ID
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
        outboxEventRepository.count() shouldBe 10L
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
        outboxEventRepository.count() shouldBe 5L
    }

    @Test
    fun `retry state survives rollback and duplicate failure reports`() {
        val order = exchangeOrderRepository.save(
            ExchangeOrder(
                userId = USER_ID,
                bankId = 0L,
                fromCurrencyId = KRW_ID,
                toCurrencyId = USD_ID,
                fromAmount = ORDER_AMOUNT,
                toAmount = BigDecimal.ZERO,
                exchangeRate = BigDecimal.ZERO,
                status = OrderStatus.QUEUED
            )
        )
        val orderId = order.id!!

        val first = exchangeOrderService.recordProcessingFailure(orderId, "lease-a", "temporary", 3)
        first.terminal shouldBe false
        assertThrows<OrderRetryNotDueException> { exchangeOrderService.processQueuedOrder(orderId) }
        val repeated = exchangeOrderService.recordProcessingFailure(orderId, "lease-a", "temporary", 3)
        repeated shouldBe first
        exchangeOrderRepository.findById(orderId).orElseThrow().retryAttempts shouldBe 1

        exchangeOrderService.recordProcessingFailure(orderId, "lease-b", "temporary", 3)
        exchangeOrderService.recordProcessingFailure(orderId, "lease-c", "persistent", 3).terminal shouldBe true

        val failed = exchangeOrderRepository.findById(orderId).orElseThrow()
        failed.status shouldBe OrderStatus.FAILED
        failed.retryAttempts shouldBe 3
        failed.nextAttemptAt shouldBe null
        exchangeOrderRepository.sumReservedFromAmount(
            USER_ID, KRW_ID, ExchangeOrderRepository.RESERVING_STATUSES
        ).compareTo(BigDecimal.ZERO) shouldBe 0
        transactionRepository.count() shouldBe 0L
        walletRepository.findByUserIdAndCurrencyId(USER_ID, KRW_ID)!!.balance shouldBe USER_KRW_BALANCE
    }

    @Test
    fun `late retry report cannot fail an already settled order`() {
        val order = exchangeOrderRepository.save(
            ExchangeOrder(
                userId = USER_ID,
                bankId = 0L,
                fromCurrencyId = KRW_ID,
                toCurrencyId = USD_ID,
                fromAmount = ORDER_AMOUNT,
                toAmount = BigDecimal.ZERO,
                exchangeRate = BigDecimal.ZERO,
                status = OrderStatus.QUEUED
            )
        )
        val orderId = order.id!!
        exchangeOrderService.processQueuedOrder(orderId)

        exchangeOrderService.recordProcessingFailure(orderId, "late-lease", "ACK timeout", 3).terminal shouldBe true
        exchangeOrderRepository.findById(orderId).orElseThrow().status shouldBe OrderStatus.SETTLED
        exchangeOrderRepository.findById(orderId).orElseThrow().retryAttempts shouldBe 0
        transactionRepository.count() shouldBe 2L
        exchangeLedgerRepository.count() shouldBe 1L
    }

    @Test
    fun `redelivered settled order does not move money or duplicate records`() {
        val order = exchangeOrderRepository.save(
            ExchangeOrder(
                userId = USER_ID,
                bankId = 0L,
                fromCurrencyId = KRW_ID,
                toCurrencyId = USD_ID,
                fromAmount = ORDER_AMOUNT,
                toAmount = BigDecimal.ZERO,
                exchangeRate = BigDecimal.ZERO,
                status = OrderStatus.QUEUED
            )
        )
        val orderId = order.id!!

        val deliveries = runConcurrently(2) {
            exchangeOrderService.processQueuedOrder(orderId)
        }

        deliveries.count { it.isSuccess } shouldBe 2
        deliveries.forEach {
            it.getOrThrow().status shouldBe org.example.backend.enums.PublicOrderStatus.COMPLETED
        }
        walletRepository.findByUserIdAndCurrencyId(USER_ID, KRW_ID)!!.balance shouldBe BigDecimal("900000.00")
        walletRepository.findByUserIdAndCurrencyId(USER_ID, USD_ID)!!.balance shouldBe BigDecimal("176.92")
        transactionRepository.count() shouldBe 2L
        exchangeLedgerRepository.count() shouldBe 1L
        walletFxHistoryRepository.count() shouldBe 3L
    }

    @Test
    fun `invalid deposits and withdrawals do not change balances or create records`() {
        for (amount in listOf(BigDecimal.ZERO, BigDecimal("-1"), BigDecimal("1.5"))) {
            val deposit = assertThrows<UserException> {
                walletService.depositFromAccount(USER_ID, KRW_ID, amount)
            }
            deposit.errorCode shouldBe ErrorCode.INVALID_TRANSFER_AMOUNT.code

            val withdrawal = assertThrows<UserException> {
                walletService.withdrawToAccount(USER_ID, KRW_ID, amount)
            }
            withdrawal.errorCode shouldBe ErrorCode.INVALID_TRANSFER_AMOUNT.code
        }
        walletRepository.findByUserIdAndCurrencyId(USER_ID, KRW_ID)!!.balance shouldBe USER_KRW_BALANCE
        accountRepository.findByBankIdAndAccountNum(1L, USER_ACCOUNT_NUM)!!.balance shouldBe BigDecimal("500000.00")
        jdbcTemplate.queryForObject("select count(*) from deposit_withdrawal", Long::class.java) shouldBe 0L
    }

    @Test
    fun `deposit moves balance and records it once`() {
        walletService.depositFromAccount(USER_ID, KRW_ID, BigDecimal("100000")) shouldBe BigDecimal("1100000.00")
        walletRepository.findByUserIdAndCurrencyId(USER_ID, KRW_ID)!!.balance shouldBe BigDecimal("1100000.00")
        accountRepository.findByBankIdAndAccountNum(1L, USER_ACCOUNT_NUM)!!.balance shouldBe BigDecimal("400000.00")
        jdbcTemplate.queryForObject("select count(*) from deposit_withdrawal", Long::class.java) shouldBe 1L
    }

    @Test
    fun `deposit checks account ownership status and currency`() {
        val account = accountRepository.findByBankIdAndAccountNum(1L, USER_ACCOUNT_NUM)!!
        account.isActive = false
        accountRepository.saveAndFlush(account)
        assertThrows<UserException> {
            walletService.depositFromAccount(USER_ID, KRW_ID, BigDecimal("1"))
        }.errorCode shouldBe ErrorCode.ACCOUNT_INACTIVE.code

        account.isActive = true
        accountRepository.saveAndFlush(account)
        jdbcTemplate.update("update account set user_id = ? where id = ?", COMPANY_USER_ID, account.id)
        assertThrows<UserException> {
            walletService.depositFromAccount(USER_ID, KRW_ID, BigDecimal("1"))
        }.errorCode shouldBe ErrorCode.INCORRECT_OWNER.code

        jdbcTemplate.update(
            "update account set user_id = ?, currency_id = ? where id = ?",
            USER_ID,
            USD_ID,
            account.id
        )
        assertThrows<UserException> {
            walletService.depositFromAccount(USER_ID, KRW_ID, BigDecimal("1"))
        }.errorCode shouldBe ErrorCode.CURRENCY_MISMATCH.code
    }

    @Test
    fun `withdrawal cannot spend funds reserved by an accepted order`() {
        exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "reserved-withdraw")

        assertThrows<UserException> {
            walletService.withdrawToAccount(USER_ID, KRW_ID, USER_KRW_BALANCE)
        }.errorCode shouldBe ErrorCode.INSUFFICIENT_WALLET_BALANCE.code

        walletService.withdrawToAccount(USER_ID, KRW_ID, BigDecimal("900000")) shouldBe BigDecimal("100000.00")
        accountRepository.findByBankIdAndAccountNum(1L, USER_ACCOUNT_NUM)!!.balance shouldBe BigDecimal("1400000.00")
        jdbcTemplate.queryForObject("select count(*) from deposit_withdrawal", Long::class.java) shouldBe 1L
    }

    @Test
    fun `concurrent order admission and withdrawal cannot overdraw available balance`() {
        jdbcTemplate.update(
            "update wallet set balance = ? where user_id = ? and currency_id = ?",
            ORDER_AMOUNT,
            USER_ID,
            KRW_ID
        )
        val results = runConcurrently(2) { index ->
            if (index == 0) {
                exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "withdraw-race")
            } else {
                walletService.withdrawToAccount(USER_ID, KRW_ID, ORDER_AMOUNT)
            }
        }

        results.count { it.isSuccess } shouldBe 1
        (results.single { it.isFailure }.exceptionOrNull() as UserException).errorCode shouldBe
            ErrorCode.INSUFFICIENT_WALLET_BALANCE.code
        val balance = walletRepository.findByUserIdAndCurrencyId(USER_ID, KRW_ID)!!.balance
        val reserved = exchangeOrderRepository.sumReservedFromAmount(
            USER_ID, KRW_ID, ExchangeOrderRepository.RESERVING_STATUSES
        )
        (balance.subtract(reserved) >= BigDecimal.ZERO) shouldBe true
        (exchangeOrderRepository.count() +
            jdbcTemplate.queryForObject("select count(*) from deposit_withdrawal", Long::class.java)!!) shouldBe 1L
    }

    @Test
    fun `account creation requires authentication and starts at zero balance`() {
        mockMvc.perform(
            post("/api/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bankId":1,"currencyId":1,"accountNum":"new-account"}""")
        ).andExpect(status().isForbidden)

        val principal = UserPrincipal(USER_ID, "user", UserType.GENERAL)
        val auth = UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_GENERAL"))
        )
        mockMvc.perform(
            post("/api/account")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bankId":1,"currencyId":1,"accountNum":"new-account"}""")
        ).andExpect(status().isCreated)

        val account = accountRepository.findByBankIdAndAccountNum(1L, "new-account")!!
        account.userId shouldBe USER_ID
        account.balance.compareTo(BigDecimal.ZERO) shouldBe 0
        assertThrows<UserException> {
            accountService.createAccount(COMPANY_USER_ID, AccountCreateRequest(1L, KRW_ID, "company-forged"))
        }.errorCode shouldBe ErrorCode.INCORRECT_OWNER.code
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
        private const val USER_ACCOUNT_NUM = "user-krw-account"
        private val RATE = BigDecimal("1300")
        private val ORDER_AMOUNT = BigDecimal("100000")
        private val USER_KRW_BALANCE = BigDecimal("1000000.00")
        private val USER_USD_BALANCE = BigDecimal("100.00")
        private val COMPANY_KRW_BALANCE = BigDecimal("1000000000.00")
        private val COMPANY_USD_BALANCE = BigDecimal("1000000.00")
        private val COMMISSION_BALANCE = BigDecimal("0.00")
    }
}
