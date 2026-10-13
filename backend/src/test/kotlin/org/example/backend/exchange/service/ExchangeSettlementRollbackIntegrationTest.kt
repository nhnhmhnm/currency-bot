package org.example.backend.exchange.service

import io.kotest.matchers.shouldBe
import org.example.backend.auth.dto.UserPrincipal
import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.enums.ExchangeType
import org.example.backend.enums.CompanyAccount
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
import org.example.backend.finance.domain.InternalAccount
import org.example.backend.finance.repository.InternalAccountRepository
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.SimulatedBankBalanceRepository
import org.example.backend.user.repository.WalletFxHistoryRepository
import org.example.backend.user.repository.WalletRepository
import org.example.backend.user.dto.AccountCreateRequest
import org.example.backend.user.service.AccountService
import org.example.backend.user.service.WalletService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
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
        "app.bank.simulation-enabled=true",
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
    private lateinit var internalAccountRepository: InternalAccountRepository

    @Autowired
    private lateinit var simulatedBankBalanceRepository: SimulatedBankBalanceRepository

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

    @Autowired
    private lateinit var redisOrderQueue: RedisOrderQueue

    @Autowired
    private lateinit var orderWorker: OrderWorker

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

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
        simulatedBankBalanceRepository.deleteAll()
        accountRepository.deleteAll()
        internalAccountRepository.deleteAll()
        jdbcTemplate.update("delete from $quotedUserTable")
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
            "insert into $quotedUserTable (id, device, password, name, phone, type, is_active, created_at) " +
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
            "insert into $quotedUserTable (id, device, password, name, phone, type, is_active, created_at) " +
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
            "insert into wallet (user_id, currency_id, balance) values (?, ?, ?)",
            USER_ID,
            KRW_ID,
            USER_KRW_BALANCE
        )
        jdbcTemplate.update(
            "insert into account (user_id, bank_id, currency_id, account_num, is_active, created_at) " +
                "values (?, ?, ?, ?, ?, current_timestamp)",
            USER_ID,
            1L,
            KRW_ID,
            USER_ACCOUNT_NUM,
            true
        )
        val userAccountId = accountRepository.findByBankIdAndAccountNum(1L, USER_ACCOUNT_NUM)!!.id!!
        jdbcTemplate.update(
            "insert into simulated_bank_balance (account_id, balance) values (?, ?)",
            userAccountId,
            BigDecimal("500000")
        )
        jdbcTemplate.update(
            "update wallet set account_id = ? where user_id = ? and currency_id = ?",
            userAccountId,
            USER_ID,
            KRW_ID
        )
        jdbcTemplate.update(
            "insert into wallet (user_id, currency_id, balance) values (?, ?, ?)",
            USER_ID,
            USD_ID,
            USER_USD_BALANCE
        )
        insertCompanyAccount(CompanyAccount.KRW_ACCOUNT, COMPANY_KRW_BALANCE)
        insertCompanyAccount(CompanyAccount.USD_ACCOUNT, COMPANY_USD_BALANCE)
        insertCompanyAccount(CompanyAccount.COMMISSION_ACCOUNT, COMMISSION_BALANCE)
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

        walletBalance(KRW_ID).compareTo(USER_KRW_BALANCE) shouldBe 0
        walletBalance(USD_ID).compareTo(USER_USD_BALANCE) shouldBe 0
        companyBalance(CompanyAccount.KRW_ACCOUNT).compareTo(COMPANY_KRW_BALANCE) shouldBe 0
        companyBalance(CompanyAccount.USD_ACCOUNT).compareTo(COMPANY_USD_BALANCE) shouldBe 0
        companyBalance(CompanyAccount.COMMISSION_ACCOUNT).compareTo(COMMISSION_BALANCE) shouldBe 0
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

        walletBalance(KRW_ID).compareTo(BigDecimal.ZERO) shouldBe 0
        walletBalance(USD_ID).compareTo(BigDecimal("869.20")) shouldBe 0
        exchangeOrderRepository.findAll().count { it.status == OrderStatus.SETTLED } shouldBe 10
        outboxEventRepository.count() shouldBe 10L
        transactionRepository.count() shouldBe 20L
        exchangeLedgerRepository.count() shouldBe 10L
        walletFxHistoryRepository.count() shouldBe 30L
        companyBalance(CompanyAccount.KRW_ACCOUNT).compareTo(BigDecimal("999000030")) shouldBe 0
        companyBalance(CompanyAccount.USD_ACCOUNT).compareTo(BigDecimal("1000769.20")) shouldBe 0
        companyBalance(CompanyAccount.COMMISSION_ACCOUNT).compareTo(BigDecimal("30")) shouldBe 0
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
        walletBalance(KRW_ID).compareTo(USER_KRW_BALANCE) shouldBe 0
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
        walletBalance(KRW_ID).compareTo(BigDecimal("900000")) shouldBe 0
        walletBalance(USD_ID).compareTo(BigDecimal("176.92")) shouldBe 0
        transactionRepository.count() shouldBe 2L
        exchangeLedgerRepository.count() shouldBe 1L
        walletFxHistoryRepository.count() shouldBe 3L
        companyBalance(CompanyAccount.KRW_ACCOUNT).compareTo(BigDecimal("999900003")) shouldBe 0
        companyBalance(CompanyAccount.USD_ACCOUNT).compareTo(BigDecimal("1000076.92")) shouldBe 0
        companyBalance(CompanyAccount.COMMISSION_ACCOUNT).compareTo(BigDecimal("3")) shouldBe 0
    }

    @Test
    fun `real Redis worker redelivery settles a MySQL order only once`() {
        assumeTrue(System.getenv("RUN_REAL_INFRA_TESTS") == "true")
        val existingKeys = redisTemplate.keys("*").orEmpty()
        check(existingKeys.isEmpty()) { "Redis DB 15 must be empty before the integration test" }

        try {
            val order = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "real-worker")
            val eventId = outboxEventRepository.findAll().single().id!!
            redisOrderQueue.enqueue(eventId, order.id) shouldBe true

            orderWorker.processQueue()
            exchangeOrderRepository.findById(order.id).orElseThrow().status shouldBe OrderStatus.SETTLED
            redisOrderQueue.enqueue(eventId + 100000, order.id) shouldBe true
            orderWorker.processQueue()

            walletBalance(KRW_ID).compareTo(BigDecimal("900000")) shouldBe 0
            walletBalance(USD_ID).compareTo(BigDecimal("176.92")) shouldBe 0
            transactionRepository.count() shouldBe 2L
            exchangeLedgerRepository.count() shouldBe 1L
            redisOrderQueue.claim() shouldBe null
        } finally {
            val testKeys = redisTemplate.keys("order:queue:*").orEmpty()
            if (testKeys.isNotEmpty()) redisTemplate.delete(testKeys)
        }
    }

    @Test
    fun `real Redis lease rejects stale ACK and recovers deferred messages`() {
        assumeTrue(System.getenv("RUN_REAL_INFRA_TESTS") == "true")
        check(redisTemplate.keys("*").orEmpty().isEmpty()) { "Redis DB 15 must be empty before the integration test" }

        try {
            redisOrderQueue.enqueue(777L, 444L) shouldBe true
            redisOrderQueue.enqueue(777L, 444L) shouldBe false
            val first = redisOrderQueue.claim()!!
            redisOrderQueue.claim() shouldBe null
            redisOrderQueue.acknowledge(first.copy(leaseId = "stale")) shouldBe false
            redisOrderQueue.defer(first.copy(leaseId = "stale"), Instant.now()) shouldBe false
            redisOrderQueue.recoverExpired(Duration.ofMillis(-1)).map { it.token } shouldBe listOf(first.token)

            val dueAt = Instant.now().plusSeconds(10)
            redisOrderQueue.defer(first, dueAt) shouldBe true
            redisOrderQueue.releaseDue(dueAt.plusSeconds(1)) shouldBe 1L
            val second = redisOrderQueue.claim()!!
            second.leaseId.equals(first.leaseId) shouldBe false
            redisOrderQueue.acknowledge(first) shouldBe false
            redisOrderQueue.acknowledge(second) shouldBe true
            redisOrderQueue.ensureQueued(444L) shouldBe true
            redisOrderQueue.acknowledge(redisOrderQueue.claim()!!) shouldBe true
        } finally {
            val testKeys = redisTemplate.keys("order:queue:*").orEmpty()
            if (testKeys.isNotEmpty()) redisTemplate.delete(testKeys)
        }
    }

    @Test
    fun `real worker recovers abandoned claims before and after MySQL settlement`() {
        assumeTrue(System.getenv("RUN_REAL_INFRA_TESTS") == "true")
        check(redisTemplate.keys("*").orEmpty().isEmpty()) { "Redis DB 15 must be empty before the integration test" }

        try {
            val pendingOrder = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "abandoned-before-commit")
            val pendingEvent = outboxEventRepository.findAll().single { it.aggregateId == pendingOrder.id }
            redisOrderQueue.enqueue(pendingEvent.id!!, pendingOrder.id) shouldBe true
            val abandoned = redisOrderQueue.claim()!!
            expireLease(abandoned.token)

            orderWorker.processQueue()
            val retryOrder = exchangeOrderRepository.findById(pendingOrder.id).orElseThrow()
            retryOrder.retryAttempts shouldBe 1
            retryOrder.status shouldBe OrderStatus.QUEUED
            transactionRepository.count() shouldBe 0L

            val retryWait = Duration.between(Instant.now(), requireNotNull(retryOrder.nextAttemptAt)).toMillis() + 100
            if (retryWait > 0) Thread.sleep(retryWait)
            orderWorker.processQueue()
            exchangeOrderRepository.findById(pendingOrder.id).orElseThrow().status shouldBe OrderStatus.SETTLED

            val committedOrder = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "abandoned-after-commit")
            val committedEvent = outboxEventRepository.findAll().single { it.aggregateId == committedOrder.id }
            redisOrderQueue.enqueue(committedEvent.id!!, committedOrder.id) shouldBe true
            val unacknowledged = redisOrderQueue.claim()!!
            exchangeOrderService.processQueuedOrder(committedOrder.id)
            expireLease(unacknowledged.token)

            orderWorker.processQueue()

            exchangeOrderRepository.findById(committedOrder.id).orElseThrow().status shouldBe OrderStatus.SETTLED
            walletBalance(KRW_ID).compareTo(BigDecimal("800000")) shouldBe 0
            walletBalance(USD_ID).compareTo(BigDecimal("253.84")) shouldBe 0
            transactionRepository.count() shouldBe 4L
            exchangeLedgerRepository.count() shouldBe 2L
            redisOrderQueue.claim() shouldBe null
        } finally {
            val testKeys = redisTemplate.keys("order:queue:*").orEmpty()
            if (testKeys.isNotEmpty()) redisTemplate.delete(testKeys)
        }
    }

    private fun expireLease(token: String) {
        redisTemplate.opsForHash<String, String>().put(
            "order:queue:lease",
            token,
            "abandoned:${Instant.now().minusSeconds(31).toEpochMilli()}"
        )
    }

    @Test
    fun `sell exchanges company assets and credits fee revenue without moving cash twice`() {
        val order = exchangeOrderService.requestSellOrder(USER_ID, "USD", BigDecimal("10"), "sell-assets")

        exchangeOrderService.processQueuedOrder(order.id)

        walletBalance(USD_ID).compareTo(BigDecimal("90")) shouldBe 0
        walletBalance(KRW_ID).compareTo(BigDecimal("1012999")) shouldBe 0
        companyBalance(CompanyAccount.USD_ACCOUNT).compareTo(COMPANY_USD_BALANCE.subtract(BigDecimal("10"))) shouldBe 0
        companyBalance(CompanyAccount.KRW_ACCOUNT).compareTo(COMPANY_KRW_BALANCE.add(BigDecimal("13000"))) shouldBe 0
        companyBalance(CompanyAccount.COMMISSION_ACCOUNT).compareTo(BigDecimal.ONE) shouldBe 0
        transactionRepository.count() shouldBe 2L
        exchangeLedgerRepository.count() shouldBe 1L
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
        walletBalance(KRW_ID).compareTo(USER_KRW_BALANCE) shouldBe 0
        simulatedBankBalance().compareTo(BigDecimal("500000")) shouldBe 0
        jdbcTemplate.queryForObject("select count(*) from deposit_withdrawal", Long::class.java) shouldBe 0L
    }

    @Test
    fun `deposit moves balance and records it once`() {
        walletService.depositFromAccount(USER_ID, KRW_ID, BigDecimal("100000")).compareTo(BigDecimal("1100000")) shouldBe 0
        walletBalance(KRW_ID).compareTo(BigDecimal("1100000")) shouldBe 0
        simulatedBankBalance().compareTo(BigDecimal("400000")) shouldBe 0
        companyBalance(CompanyAccount.KRW_ACCOUNT).compareTo(COMPANY_KRW_BALANCE.add(BigDecimal("100000"))) shouldBe 0
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

        walletService.withdrawToAccount(USER_ID, KRW_ID, BigDecimal("900000")).compareTo(BigDecimal("100000")) shouldBe 0
        simulatedBankBalance().compareTo(BigDecimal("1400000")) shouldBe 0
        companyBalance(CompanyAccount.KRW_ACCOUNT).compareTo(COMPANY_KRW_BALANCE.subtract(BigDecimal("900000"))) shouldBe 0
        jdbcTemplate.queryForObject("select count(*) from deposit_withdrawal", Long::class.java) shouldBe 1L
    }

    @Test
    fun `withdrawal with insufficient company cash leaves all balances unchanged`() {
        val cash = internalAccountRepository.findByCode(CompanyAccount.KRW_ACCOUNT)!!
        cash.balance = BigDecimal.ZERO
        internalAccountRepository.saveAndFlush(cash)

        assertThrows<UserException> {
            walletService.withdrawToAccount(USER_ID, KRW_ID, BigDecimal.ONE)
        }.errorCode shouldBe ErrorCode.COMPANY_LIQUIDITY_INSUFFICIENT.code

        walletBalance(KRW_ID).compareTo(USER_KRW_BALANCE) shouldBe 0
        companyBalance(CompanyAccount.KRW_ACCOUNT).compareTo(BigDecimal.ZERO) shouldBe 0
        simulatedBankBalance().compareTo(BigDecimal("500000")) shouldBe 0
        jdbcTemplate.queryForObject("select count(*) from deposit_withdrawal", Long::class.java) shouldBe 0L
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
        simulatedBankBalanceRepository.findById(account.id!!).orElseThrow().balance.compareTo(BigDecimal.ZERO) shouldBe 0
        assertThrows<UserException> {
            accountService.createAccount(COMPANY_USER_ID, AccountCreateRequest(1L, KRW_ID, "company-forged"))
        }.errorCode shouldBe ErrorCode.INCORRECT_OWNER.code
    }

    @Test
    fun `wallet and external account uniqueness is enforced by the database`() {
        assertThrows<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "insert into wallet (user_id, currency_id, balance) values (?, ?, ?)",
                USER_ID,
                KRW_ID,
                BigDecimal.ZERO
            )
        }
        assertThrows<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "insert into account (user_id, bank_id, currency_id, account_num, is_active, created_at) " +
                    "values (?, ?, ?, ?, ?, current_timestamp)",
                USER_ID,
                1L,
                KRW_ID,
                USER_ACCOUNT_NUM,
                true
            )
        }
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

    private fun insertCompanyAccount(code: CompanyAccount, balance: BigDecimal) {
        internalAccountRepository.save(InternalAccount(code = code, currencyId = code.currencyId, balance = balance))
    }

    private fun companyBalance(code: CompanyAccount): BigDecimal {
        return internalAccountRepository.findByCode(code)!!.balance
    }

    private fun walletBalance(currencyId: Long): BigDecimal {
        return walletRepository.findByUserIdAndCurrencyId(USER_ID, currencyId)!!.balance
    }

    private fun simulatedBankBalance(): BigDecimal {
        val accountId = accountRepository.findByBankIdAndAccountNum(1L, USER_ACCOUNT_NUM)!!.id!!
        return simulatedBankBalanceRepository.findById(accountId).orElseThrow().balance
    }

    private val quotedUserTable: String
        get() = jdbcTemplate.dataSource!!.connection.use { connection ->
            val quote = connection.metaData.identifierQuoteString.trim()
            "${quote}user$quote"
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
        @JvmStatic
        @DynamicPropertySource
        fun realInfrastructureProperties(registry: DynamicPropertyRegistry) {
            if (System.getenv("RUN_REAL_INFRA_TESTS") != "true") return
            val password = requireNotNull(System.getenv("CURRENCY_TEST_MYSQL_PASSWORD")) {
                "CURRENCY_TEST_MYSQL_PASSWORD is required for the isolated MySQL test database"
            }
            val url = "jdbc:mysql://127.0.0.1:3306/currency_bot_integration_test?useSSL=false&allowPublicKeyRetrieval=true"
            DriverManager.getConnection(url, "root", password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SHOW TABLES").use { tables ->
                        check(!tables.next()) { "The isolated MySQL test database must be empty" }
                    }
                }
            }
            registry.add("spring.datasource.url") { url }
            registry.add("spring.datasource.username") { "root" }
            registry.add("spring.datasource.password") { password }
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.MySQLDialect" }
            registry.add("spring.data.redis.database") { 15 }
        }

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
