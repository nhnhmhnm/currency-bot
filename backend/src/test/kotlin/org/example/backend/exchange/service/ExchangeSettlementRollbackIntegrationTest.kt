package org.example.backend.exchange.service

import io.kotest.matchers.shouldBe
import org.example.backend.auth.dto.UserPrincipal
import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.enums.ExchangeType
import org.example.backend.enums.CompanyAccount
import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.enums.OutboxEventType
import org.example.backend.enums.OutboxEventStatus
import org.example.backend.enums.UserType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.domain.ReservationStatus
import org.example.backend.exchange.dto.BestExchangeRateDto
import org.example.backend.exchange.dto.ExchangeLedgerCommand
import org.example.backend.exchange.dto.ExchangeLedgerResponse
import org.example.backend.exchange.repository.ExchangeLedgerRepository
import org.example.backend.exchange.repository.CompletedOrderViewRepository
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.exchange.repository.ExchangeReservationRepository
import org.example.backend.exchange.repository.OutboxEventRepository
import org.example.backend.exchange.repository.TransactionRepository
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.domain.InternalAccount
import org.example.backend.finance.repository.InternalAccountRepository
import org.example.backend.common.util.RedisCompletionQueue
import org.example.backend.finance.repository.JournalRepository
import org.example.backend.finance.repository.JournalLineRepository
import org.example.backend.finance.service.JournalReconciliationService
import org.example.backend.finance.domain.JournalSource
import org.example.backend.finance.domain.Journal
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
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.DriverManager
import java.sql.DatabaseMetaData
import java.sql.SQLException
import java.sql.Types
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    private lateinit var reservationRepository: ExchangeReservationRepository

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var internalAccountRepository: InternalAccountRepository

    @Autowired
    private lateinit var journalRepository: JournalRepository

    @Autowired
    private lateinit var journalLineRepository: JournalLineRepository

    @Autowired
    private lateinit var journalReconciliationService: JournalReconciliationService

    @Autowired
    private lateinit var simulatedBankBalanceRepository: SimulatedBankBalanceRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var exchangeLedgerRepository: ExchangeLedgerRepository

    @Autowired
    private lateinit var completedOrderViewRepository: CompletedOrderViewRepository

    @Autowired
    private lateinit var completedOrderWorker: CompletedOrderWorker

    @Autowired
    private lateinit var completedOrderProjectionService: CompletedOrderProjectionService

    @Autowired
    private lateinit var redisCompletionQueue: RedisCompletionQueue

    @Autowired
    private lateinit var walletFxHistoryRepository: WalletFxHistoryRepository

    @Autowired
    private lateinit var ledgerService: ControllableLedgerService

    @Autowired
    private lateinit var fixedExchangeService: FixedExchangeService

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
    private lateinit var outboxEventPublisher: OutboxEventPublisher

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun setUp() {
        ledgerService.failOnRecord = false
        fixedExchangeService.buyRate = RATE
        fixedExchangeService.sellRate = RATE
        fixedExchangeService.buyBaseRate = RATE
        fixedExchangeService.sellBaseRate = RATE
        walletFxHistoryRepository.deleteAll()
        jdbcTemplate.update("delete from journal_line")
        jdbcTemplate.update("delete from journal")
        jdbcTemplate.update("delete from deposit_withdrawal")
        exchangeLedgerRepository.deleteAll()
        completedOrderViewRepository.deleteAll()
        transactionRepository.deleteAll()
        outboxEventRepository.deleteAll()
        reservationRepository.deleteAll()
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
    fun `accepted order reserves funds and quota then consumes reservation exactly once`() {
        val accepted = exchangeOrderService.requestBuyOrder(
            USER_ID, "USD", ORDER_AMOUNT, "reservation-once", expectedRate = RATE
        )
        val reservation = reservationRepository.findById(accepted.id).orElseThrow()
        reservation.status shouldBe ReservationStatus.ACTIVE
        reservation.fromAmount.compareTo(ORDER_AMOUNT) shouldBe 0
        reservation.businessDate shouldBe LocalDate.now(ZoneId.of("Asia/Seoul"))
        reservationRepository.sumActiveFunds(USER_ID, KRW_ID).compareTo(ORDER_AMOUNT) shouldBe 0
        exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "reservation-once", expectedRate = RATE).id shouldBe accepted.id
        reservationRepository.count() shouldBe 1L

        exchangeOrderService.processQueuedOrder(accepted.id)
        exchangeOrderService.processQueuedOrder(accepted.id)

        reservationRepository.findById(accepted.id).orElseThrow().status shouldBe ReservationStatus.CONSUMED
        reservationRepository.sumActiveFunds(USER_ID, KRW_ID).compareTo(BigDecimal.ZERO) shouldBe 0
        transactionRepository.count() shouldBe 2L
        exchangeLedgerRepository.count() shouldBe 1L
        journalRepository.count() shouldBe 1L
        journalLineRepository.count() shouldBe 5L
        journalReconciliationService.audit().balanced shouldBe true
    }

    @Test
    fun `completion projection is idempotent and visible only to the order owner`() {
        val accepted = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "completion-view")
        exchangeOrderService.processQueuedOrder(accepted.id)
        val event = outboxEventRepository.findAll().single { it.eventType == OutboxEventType.ORDER_COMPLETED }
        val before = balanceSnapshot()

        completedOrderProjectionService.apply(event.id!!)
        completedOrderProjectionService.apply(event.id!!)

        completedOrderViewRepository.count() shouldBe 1L
        completedOrderProjectionService.history(USER_ID, 20).single().orderId shouldBe accepted.id
        completedOrderProjectionService.history(SECOND_USER_ID, 20).size shouldBe 0
        fun auth(userId: Long) = authentication(UsernamePasswordAuthenticationToken(
            UserPrincipal(userId, "user", UserType.GENERAL), null, listOf(SimpleGrantedAuthority("ROLE_GENERAL"))
        ))
        mockMvc.perform(get("/api/exchange/history").with(auth(USER_ID)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].orderId").value(accepted.id))
            .andExpect(jsonPath("$[0].eventId").doesNotExist())
            .andExpect(jsonPath("$[0].userId").doesNotExist())
        mockMvc.perform(get("/api/exchange/history").with(auth(SECOND_USER_ID)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
        balanceSnapshot() shouldBe before
    }

    @Test
    fun `completion events remain pending for a separate worker without blocking settlement`() {
        val accepted = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "completion-independent")
        exchangeOrderService.processQueuedOrder(accepted.id)

        exchangeOrderRepository.findById(accepted.id).orElseThrow().status shouldBe OrderStatus.SETTLED
        journalRepository.count() shouldBe 1L
        completedOrderViewRepository.count() shouldBe 0L
        outboxEventRepository.findAll().single { it.eventType == OutboxEventType.ORDER_COMPLETED }.status shouldBe OutboxEventStatus.PENDING
    }

    @Test
    fun `out of order completion events produce the same read model`() {
        val first = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "projection-first")
        val second = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "projection-second")
        exchangeOrderService.processQueuedOrder(first.id)
        exchangeOrderService.processQueuedOrder(second.id)
        val events = outboxEventRepository.findAll().filter { it.eventType == OutboxEventType.ORDER_COMPLETED }
            .associateBy { it.aggregateId }

        completedOrderProjectionService.apply(events.getValue(second.id).id!!)
        completedOrderProjectionService.apply(events.getValue(first.id).id!!)

        completedOrderProjectionService.history(USER_ID, 20).map { it.orderId }.toSet() shouldBe setOf(first.id, second.id)
        transactionRepository.count() shouldBe 4L
        journalRepository.count() shouldBe 2L
    }

    @Test
    fun `rate change cancels an accepted order and releases both reservations`() {
        val accepted = exchangeOrderService.requestBuyOrder(
            USER_ID, "USD", ORDER_AMOUNT, "rate-change", expectedRate = RATE
        )
        fixedExchangeService.buyRate = RATE.add(BigDecimal.ONE)

        val canceled = exchangeOrderService.processQueuedOrder(accepted.id)

        canceled.status shouldBe org.example.backend.enums.PublicOrderStatus.CANCELED
        canceled.cancellationReason shouldBe "RATE_CHANGED"
        exchangeOrderRepository.findById(accepted.id).orElseThrow().status shouldBe OrderStatus.CANCELED
        reservationRepository.findById(accepted.id).orElseThrow().status shouldBe ReservationStatus.RELEASED
        reservationRepository.sumActiveFunds(USER_ID, KRW_ID).compareTo(BigDecimal.ZERO) shouldBe 0
        walletBalance(KRW_ID).compareTo(USER_KRW_BALANCE) shouldBe 0
        transactionRepository.count() shouldBe 0L
        exchangeLedgerRepository.count() shouldBe 0L
        exchangeOrderService.processQueuedOrder(accepted.id).cancellationReason shouldBe "RATE_CHANGED"
    }

    @Test
    fun `stale client quote is rejected without creating order reservation or outbox`() {
        assertThrows<UserException> {
            exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "stale-quote", expectedRate = RATE.subtract(BigDecimal.ONE))
        }.errorCode shouldBe ErrorCode.RATE_CHANGED.code
        exchangeOrderRepository.count() shouldBe 0L
        reservationRepository.count() shouldBe 0L
        outboxEventRepository.count() shouldBe 0L
    }

    @Test
    fun `final failure releases reservation without changing the wallet`() {
        val accepted = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "final-failure")
        exchangeOrderService.recordProcessingFailure(accepted.id, "failure-1", "internal details", 1).terminal shouldBe true
        exchangeOrderService.recordProcessingFailure(accepted.id, "failure-1", "internal details", 1).terminal shouldBe true

        reservationRepository.findById(accepted.id).orElseThrow().status shouldBe ReservationStatus.RELEASED
        reservationRepository.sumActiveFunds(USER_ID, KRW_ID).compareTo(BigDecimal.ZERO) shouldBe 0
        exchangeOrderRepository.findById(accepted.id).orElseThrow().status shouldBe OrderStatus.FAILED
        walletBalance(KRW_ID).compareTo(USER_KRW_BALANCE) shouldBe 0
        transactionRepository.count() shouldBe 0L
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
        journalRepository.count() shouldBe 0L
        journalLineRepository.count() shouldBe 0L
        outboxEventRepository.count() shouldBe 0L
        exchangeOrderRepository.findById(order.id!!).orElseThrow().status shouldBe OrderStatus.QUEUED
    }

    @Test
    fun `reconciliation detects a missing settlement journal and balance drift`() {
        val accepted = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "journal-reconcile")
        exchangeOrderService.processQueuedOrder(accepted.id)
        journalRepository.existsBySourceTypeAndSourceId(JournalSource.EXCHANGE_ORDER, accepted.id) shouldBe true
        journalReconciliationService.audit().balanced shouldBe true

        val cash = internalAccountRepository.findByCode(CompanyAccount.KRW_ACCOUNT)!!
        cash.balance = cash.balance.add(BigDecimal.ONE)
        internalAccountRepository.saveAndFlush(cash)
        journalReconciliationService.audit().issues.any { it.contains("differs from journal") } shouldBe true

        journalLineRepository.deleteAll()
        journalRepository.deleteAll()
        journalReconciliationService.audit().issues.any { it.contains("has no journal") } shouldBe true
    }

    @Test
    fun `duplicate settlement journal rejects the entire balance movement`() {
        val accepted = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "journal-unique")
        journalRepository.saveAndFlush(Journal(sourceType = JournalSource.EXCHANGE_ORDER, sourceId = accepted.id))

        assertThrows<Exception> { exchangeOrderService.processQueuedOrder(accepted.id) }

        exchangeOrderRepository.findById(accepted.id).orElseThrow().status shouldBe OrderStatus.QUEUED
        walletBalance(KRW_ID).compareTo(USER_KRW_BALANCE) shouldBe 0
        companyBalance(CompanyAccount.KRW_ACCOUNT).compareTo(COMPANY_KRW_BALANCE) shouldBe 0
        walletFxHistoryRepository.count() shouldBe 0L
        transactionRepository.count() shouldBe 0L
        journalRepository.count() shouldBe 1L
        journalReconciliationService.audit().issues.any { it.contains("has no settled order") } shouldBe true
    }

    @Test
    fun `company cash shortage after wallet posting rolls back the whole settlement`() {
        val cash = internalAccountRepository.findByCode(CompanyAccount.KRW_ACCOUNT)!!
        cash.balance = BigDecimal.ZERO
        internalAccountRepository.saveAndFlush(cash)
        val order = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "short-company-cash")

        assertThrows<UserException> {
            exchangeOrderService.processQueuedOrder(order.id)
        }.errorCode shouldBe ErrorCode.COMPANY_LIQUIDITY_INSUFFICIENT.code

        walletBalance(KRW_ID).compareTo(USER_KRW_BALANCE) shouldBe 0
        walletBalance(USD_ID).compareTo(USER_USD_BALANCE) shouldBe 0
        companyBalance(CompanyAccount.KRW_ACCOUNT).compareTo(BigDecimal.ZERO) shouldBe 0
        companyBalance(CompanyAccount.USD_ACCOUNT).compareTo(COMPANY_USD_BALANCE) shouldBe 0
        companyBalance(CompanyAccount.COMMISSION_ACCOUNT).compareTo(COMMISSION_BALANCE) shouldBe 0
        walletFxHistoryRepository.count() shouldBe 0L
        transactionRepository.count() shouldBe 0L
        exchangeLedgerRepository.count() shouldBe 0L
        outboxEventRepository.count() shouldBe 1L
        exchangeOrderRepository.findById(order.id).orElseThrow().status shouldBe OrderStatus.QUEUED
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
        outboxEventRepository.count() shouldBe 20L
        outboxEventRepository.findAll().count { it.eventType == OutboxEventType.ORDER_COMPLETED } shouldBe 10
        transactionRepository.count() shouldBe 20L
        exchangeLedgerRepository.count() shouldBe 10L
        walletFxHistoryRepository.count() shouldBe 30L
        companyBalance(CompanyAccount.KRW_ACCOUNT).compareTo(BigDecimal("999000030")) shouldBe 0
        companyBalance(CompanyAccount.USD_ACCOUNT).compareTo(BigDecimal("1000769.20")) shouldBe 0
        companyBalance(CompanyAccount.COMMISSION_ACCOUNT).compareTo(BigDecimal("30")) shouldBe 0
    }

    @Test
    fun `ordinary exchange orders have no product daily USD quota`() {
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

        requests.count { it.isSuccess } shouldBe 6
        exchangeOrderRepository.count() shouldBe 6L
        reservationRepository.count() shouldBe 6L
        outboxEventRepository.count() shouldBe 6L
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
            val testKeys = redisTemplate.keys("order:queue:*").orEmpty() + redisTemplate.keys("order:completion:*").orEmpty()
            if (testKeys.isNotEmpty()) redisTemplate.delete(testKeys)
        }
    }

    @Test
    fun `real InnoDB deadlock rolls back one order and Redis replay settles it once`() {
        assumeTrue(System.getenv("RUN_REAL_INFRA_TESTS") == "true")
        check(redisTemplate.keys("*").orEmpty().isEmpty()) { "Redis DB 15 must be empty before the integration test" }
        insertExchangeUser(SECOND_USER_ID, BigDecimal.ZERO, BigDecimal("100"))
        val buy = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "deadlock-buy")
        val sell = exchangeOrderService.requestSellOrder(SECOND_USER_ID, "USD", BigDecimal("10"), "deadlock-sell")
        val orders = listOf(buy, sell)
        val firstLocks = listOf(CompanyAccount.KRW_ACCOUNT, CompanyAccount.USD_ACCOUNT)
        val bothLocked = CountDownLatch(2)

        try {
            val attempts = runConcurrently(2) { index ->
                TransactionTemplate(transactionManager).executeWithoutResult {
                    jdbcTemplate.queryForObject(
                        "select balance from internal_account where code = ? for update",
                        BigDecimal::class.java,
                        firstLocks[index].name
                    )
                    bothLocked.countDown()
                    check(bothLocked.await(5, TimeUnit.SECONDS)) { "Both initial locks were not acquired" }
                    exchangeOrderService.processQueuedOrder(orders[index].id)
                }
            }

            attempts.count { it.isSuccess } shouldBe 1
            val victimIndex = attempts.indexOfFirst { it.isFailure }
            val failure = requireNotNull(attempts[victimIndex].exceptionOrNull())
            val sqlFailure = generateSequence(failure) { it.cause }
                .filterIsInstance<SQLException>().firstOrNull()
            check(sqlFailure != null) { "Expected an InnoDB deadlock error" }
            sqlFailure.sqlState shouldBe "40001"
            sqlFailure.errorCode shouldBe 1213
            exchangeOrderRepository.findById(orders[victimIndex].id).orElseThrow().status shouldBe OrderStatus.QUEUED
            exchangeOrderRepository.findAll().count { it.status == OrderStatus.SETTLED } shouldBe 1
            transactionRepository.count() shouldBe 2L
            exchangeLedgerRepository.count() shouldBe 1L
            outboxEventRepository.findAll().count { it.eventType == OutboxEventType.ORDER_COMPLETED } shouldBe 1

            outboxEventPublisher.publishPendingOrderEvents()
            orderWorker.processQueue()

            exchangeOrderRepository.findAll().count { it.status == OrderStatus.SETTLED } shouldBe 2
            transactionRepository.count() shouldBe 4L
            exchangeLedgerRepository.count() shouldBe 2L
            outboxEventRepository.findAll().count { it.eventType == OutboxEventType.ORDER_COMPLETED } shouldBe 2
            walletBalance(KRW_ID).compareTo(BigDecimal("900000")) shouldBe 0
            walletBalance(USD_ID).compareTo(BigDecimal("176.92")) shouldBe 0
            walletRepository.findByUserIdAndCurrencyId(SECOND_USER_ID, KRW_ID)!!.balance
                .compareTo(BigDecimal("12999")) shouldBe 0
            walletRepository.findByUserIdAndCurrencyId(SECOND_USER_ID, USD_ID)!!.balance
                .compareTo(BigDecimal("90")) shouldBe 0
            companyBalance(CompanyAccount.KRW_ACCOUNT).compareTo(BigDecimal("999913003")) shouldBe 0
            companyBalance(CompanyAccount.USD_ACCOUNT).compareTo(BigDecimal("1000066.92")) shouldBe 0
            companyBalance(CompanyAccount.COMMISSION_ACCOUNT).compareTo(BigDecimal("4")) shouldBe 0
            redisOrderQueue.claim() shouldBe null
            println("REAL_DEADLOCK victim_order_id=${orders[victimIndex].id} sqlstate=${sqlFailure.sqlState} code=${sqlFailure.errorCode} replay=settled_once")
        } finally {
            val testKeys = redisTemplate.keys("order:queue:*").orEmpty() + redisTemplate.keys("order:completion:*").orEmpty()
            if (testKeys.isNotEmpty()) redisTemplate.delete(testKeys)
        }
    }

    @Test
    fun `real single worker baseline measures a hundred accepted and settled orders`() {
        assumeTrue(System.getenv("RUN_REAL_INFRA_TESTS") == "true")
        assumeTrue(System.getenv("RUN_LOAD_BASELINE_TESTS") == "true")
        check(redisTemplate.keys("*").orEmpty().isEmpty()) { "Redis DB 15 must be empty before the integration test" }
        for (index in 1 until 20) {
            insertExchangeUser(1000L + index, USER_KRW_BALANCE, USER_USD_BALANCE)
        }

        try {
            val admissionStart = System.nanoTime()
            val requests = runConcurrently(20) { index ->
                val userId = if (index == 0) USER_ID else 1000L + index
                (0 until 5).map { orderIndex ->
                    exchangeOrderService.requestBuyOrder(
                        userId, "USD", ORDER_AMOUNT, "load-$userId-$orderIndex"
                    )
                }
            }.flatMap { it.getOrThrow() }
            val admissionMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - admissionStart)
            requests.size shouldBe 100

            val publishStart = System.nanoTime()
            outboxEventPublisher.publishPendingOrderEvents()
            val publishMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - publishStart)

            val settlementStart = System.nanoTime()
            orderWorker.processQueue()
            val settlementMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - settlementStart)

            val orders = exchangeOrderRepository.findAll()
            orders.size shouldBe 100
            orders.count { it.status == OrderStatus.SETTLED && it.retryAttempts == 0 } shouldBe 100
            transactionRepository.count() shouldBe 200L
            exchangeLedgerRepository.count() shouldBe 100L
            walletFxHistoryRepository.count() shouldBe 300L
            outboxEventRepository.count() shouldBe 200L
            walletBalance(KRW_ID).compareTo(BigDecimal("500000")) shouldBe 0
            walletBalance(USD_ID).compareTo(BigDecimal("484.60")) shouldBe 0
            companyBalance(CompanyAccount.KRW_ACCOUNT)
                .compareTo(COMPANY_KRW_BALANCE.subtract(BigDecimal("9999700"))) shouldBe 0
            companyBalance(CompanyAccount.USD_ACCOUNT)
                .compareTo(COMPANY_USD_BALANCE.add(BigDecimal("7692"))) shouldBe 0
            companyBalance(CompanyAccount.COMMISSION_ACCOUNT).compareTo(BigDecimal("300")) shouldBe 0
            redisOrderQueue.claim() shouldBe null

            val completionMs = orders.map {
                Duration.between(requireNotNull(it.requestedAt), requireNotNull(it.completedAt)).toMillis()
            }.sorted()
            val throughput = String.format(java.util.Locale.ROOT, "%.2f", 100_000.0 / settlementMs)
            println(
                "LOAD_BASELINE orders=100 users=20 admission_ms=$admissionMs publish_ms=$publishMs " +
                    "settlement_ms=$settlementMs settlement_orders_per_sec=$throughput " +
                    "completion_p50_ms=${completionMs[49]} completion_p95_ms=${completionMs[94]} " +
                    "completion_max_ms=${completionMs.last()}"
            )
        } finally {
            val testKeys = redisTemplate.keys("order:queue:*").orEmpty() + redisTemplate.keys("order:completion:*").orEmpty()
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
            val recovered = redisOrderQueue.recoverExpired(Duration.ZERO).single()
            recovered.token shouldBe first.token
            redisOrderQueue.acknowledge(first) shouldBe false

            val dueAt = Instant.now().plusSeconds(10)
            redisOrderQueue.defer(recovered, dueAt) shouldBe true
            redisOrderQueue.releaseDue(dueAt.plusSeconds(1)) shouldBe 1L
            val second = redisOrderQueue.claim()!!
            second.leaseId.equals(first.leaseId) shouldBe false
            redisOrderQueue.acknowledge(first) shouldBe false
            redisOrderQueue.acknowledge(second) shouldBe true
            redisOrderQueue.ensureQueued(444L) shouldBe true
            redisOrderQueue.acknowledge(redisOrderQueue.claim()!!) shouldBe true
        } finally {
            val testKeys = redisTemplate.keys("order:queue:*").orEmpty() + redisTemplate.keys("order:completion:*").orEmpty()
            if (testKeys.isNotEmpty()) redisTemplate.delete(testKeys)
        }
    }

    @Test
    fun `real completion stream recovers a lost publish and prevents duplicate projections`() {
        assumeTrue(System.getenv("RUN_REAL_INFRA_TESTS") == "true")
        check(redisTemplate.keys("*").orEmpty().isEmpty()) { "Redis DB 15 must be empty before the integration test" }

        try {
            val accepted = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "completion-stream")
            exchangeOrderService.processQueuedOrder(accepted.id)
            val event = outboxEventRepository.findAll().single { it.eventType == OutboxEventType.ORDER_COMPLETED }
            outboxEventPublisher.publishPendingOrderEvents()
            outboxEventRepository.findById(event.id!!).orElseThrow().status shouldBe OutboxEventStatus.PUBLISHED
            redisTemplate.delete("order:completion:stream")

            completedOrderWorker.recoverUnprojected()
            val abandoned = redisCompletionQueue.claim()!!
            val recovered = redisCompletionQueue.recoverExpired(Duration.ZERO).single()
            redisCompletionQueue.acknowledge(abandoned) shouldBe false
            completedOrderProjectionService.apply(recovered.subjectId)
            redisCompletionQueue.acknowledge(recovered) shouldBe true

            redisCompletionQueue.ensureQueued(event.id!!) shouldBe false
            completedOrderViewRepository.count() shouldBe 1L
            completedOrderProjectionService.apply(event.id!!)
            completedOrderViewRepository.count() shouldBe 1L
            exchangeOrderRepository.findById(accepted.id).orElseThrow().status shouldBe OrderStatus.SETTLED
            journalRepository.count() shouldBe 1L
        } finally {
            val testKeys = redisTemplate.keys("order:queue:*").orEmpty() + redisTemplate.keys("order:completion:*").orEmpty()
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
            val testKeys = redisTemplate.keys("order:queue:*").orEmpty() + redisTemplate.keys("order:completion:*").orEmpty()
            if (testKeys.isNotEmpty()) redisTemplate.delete(testKeys)
        }
    }

    private fun expireLease(token: String) {
        redisTemplate.opsForStream<String, String>().claim(
            "order:queue:stream",
            "order-settlement",
            "abandoned",
            XClaimOptions.minIdle(Duration.ZERO).ids(token).idle(Duration.ofSeconds(31))
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
    fun `opposite direction orders for different users settle with one lock order`() {
        insertExchangeUser(SECOND_USER_ID, BigDecimal.ZERO, BigDecimal("100"))
        val buy = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "opposite-buy")
        val sell = exchangeOrderService.requestSellOrder(SECOND_USER_ID, "USD", BigDecimal("10"), "opposite-sell")

        val results = runConcurrently(2) { index ->
            exchangeOrderService.processQueuedOrder(if (index == 0) buy.id else sell.id)
        }

        results.forEach { it.getOrThrow() }
        exchangeOrderRepository.findAll().count { it.status == OrderStatus.SETTLED } shouldBe 2
        walletBalance(KRW_ID).compareTo(BigDecimal("900000")) shouldBe 0
        walletBalance(USD_ID).compareTo(BigDecimal("176.92")) shouldBe 0
        walletRepository.findByUserIdAndCurrencyId(SECOND_USER_ID, KRW_ID)!!.balance
            .compareTo(BigDecimal("12999")) shouldBe 0
        walletRepository.findByUserIdAndCurrencyId(SECOND_USER_ID, USD_ID)!!.balance
            .compareTo(BigDecimal("90")) shouldBe 0
        companyBalance(CompanyAccount.KRW_ACCOUNT).compareTo(BigDecimal("999913003")) shouldBe 0
        companyBalance(CompanyAccount.USD_ACCOUNT).compareTo(BigDecimal("1000066.92")) shouldBe 0
        companyBalance(CompanyAccount.COMMISSION_ACCOUNT).compareTo(BigDecimal("4")) shouldBe 0
        transactionRepository.count() shouldBe 4L
        exchangeLedgerRepository.count() shouldBe 2L
        outboxEventRepository.findAll().count { it.eventType == OutboxEventType.ORDER_COMPLETED } shouldBe 2
    }

    @Test
    fun `arbitrage reserves only parent and records one completion event`() {
        fixedExchangeService.sellBaseRate = BigDecimal("1310")
        val parent = exchangeOrderService.requestArbitrageOrder(USER_ID, "USD", BigDecimal("1000000"), "parent-arbitrage")

        exchangeOrderService.processQueuedOrder(parent.id)

        exchangeOrderRepository.findAll().count { it.status == OrderStatus.SETTLED } shouldBe 3
        transactionRepository.count() shouldBe 4L
        exchangeLedgerRepository.count() shouldBe 2L
        outboxEventRepository.count() shouldBe 2L
        outboxEventRepository.findAll().count { it.eventType == OutboxEventType.ORDER_COMPLETED } shouldBe 1
        journalRepository.count() shouldBe 2L
        journalRepository.existsBySourceTypeAndSourceId(JournalSource.EXCHANGE_ORDER, parent.id) shouldBe false
        journalReconciliationService.audit().balanced shouldBe true
        reservationRepository.findById(parent.id).orElseThrow().status shouldBe ReservationStatus.CONSUMED
        reservationRepository.sumActiveFunds(USER_ID, KRW_ID).compareTo(BigDecimal.ZERO) shouldBe 0
        walletBalance(KRW_ID).compareTo(BigDecimal("1007660")) shouldBe 0
        walletBalance(USD_ID).compareTo(USER_USD_BALANCE) shouldBe 0
        companyBalance(CompanyAccount.COMMISSION_ACCOUNT).compareTo(BigDecimal("31")) shouldBe 0
    }

    @Test
    fun `arbitrage shares four daily slots across currencies and releases canceled slots`() {
        jdbcTemplate.update(
            "insert into currency (id, code, name, scale, unit) values (?, ?, ?, ?, ?)",
            JPY_ID, "JPY", "Japanese Yen", 0, BigDecimal("100")
        )
        jdbcTemplate.update(
            "insert into wallet (user_id, currency_id, balance) values (?, ?, ?)",
            USER_ID, JPY_ID, BigDecimal.ZERO
        )
        insertCompanyAccount(CompanyAccount.JPY_ACCOUNT, COMPANY_USD_BALANCE)
        fixedExchangeService.sellBaseRate = BigDecimal("1310")
        val amount = BigDecimal("1000000")
        val first = exchangeOrderService.requestArbitrageOrder(USER_ID, "USD", amount, "arb-first")
        exchangeOrderRepository.findById(first.id).orElseThrow().arbitrageSlotNumber shouldBe 1

        fixedExchangeService.sellBaseRate = BigDecimal("1320")
        exchangeOrderService.processQueuedOrder(first.id).cancellationReason shouldBe "RATE_CHANGED"
        reservationRepository.findById(first.id).orElseThrow().status shouldBe ReservationStatus.RELEASED
        fixedExchangeService.sellBaseRate = BigDecimal("1310")
        val second = exchangeOrderService.requestArbitrageOrder(USER_ID, "JPY", amount, "arb-second")
        exchangeOrderRepository.findById(second.id).orElseThrow().arbitrageSlotNumber shouldBe 1
        exchangeOrderService.processQueuedOrder(second.id)
        for (slot in 2..4) {
            val order = exchangeOrderService.requestArbitrageOrder(USER_ID, "USD", amount, "arb-$slot")
            exchangeOrderRepository.findById(order.id).orElseThrow().arbitrageSlotNumber shouldBe slot
            exchangeOrderService.processQueuedOrder(order.id)
        }
        assertThrows<UserException> {
            exchangeOrderService.requestArbitrageOrder(USER_ID, "USD", amount, "arb-fifth")
        }.errorCode shouldBe ErrorCode.ARBITRAGE_DAILY_LIMIT_EXCEEDED.code
    }

    @Test
    fun `only the order owner sees a public cancellation reason`() {
        fixedExchangeService.sellBaseRate = BigDecimal("1310")
        val order = exchangeOrderService.requestArbitrageOrder(USER_ID, "USD", BigDecimal("1000000"), "visible-cancel")
        fixedExchangeService.sellBaseRate = BigDecimal("1320")
        exchangeOrderService.processQueuedOrder(order.id)

        fun auth(userId: Long) = authentication(UsernamePasswordAuthenticationToken(
            UserPrincipal(userId, "user", UserType.GENERAL), null, listOf(SimpleGrantedAuthority("ROLE_GENERAL"))
        ))
        mockMvc.perform(get("/api/exchange/orders/${order.id}").with(auth(USER_ID)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELED"))
            .andExpect(jsonPath("$.cancellationReason").value("RATE_CHANGED"))
        mockMvc.perform(get("/api/exchange/orders/${order.id}").with(auth(SECOND_USER_ID)))
            .andExpect(status().isNotFound)
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
        journalRepository.count() shouldBe 1L
        journalLineRepository.count() shouldBe 2L
        journalReconciliationService.audit().balanced shouldBe true
    }

    @Test
    fun `deposit buy sell and withdrawal keep cash wallet and fee deltas balanced`() {
        var before = balanceSnapshot()

        walletService.depositFromAccount(USER_ID, KRW_ID, BigDecimal("100000"))
        before = assertBalancedChange(before, BigDecimal("-100000"))

        val buy = exchangeOrderService.requestBuyOrder(USER_ID, "USD", ORDER_AMOUNT, "balanced-buy")
        exchangeOrderService.processQueuedOrder(buy.id)
        before = assertBalancedChange(before, BigDecimal.ZERO)

        val sell = exchangeOrderService.requestSellOrder(USER_ID, "USD", BigDecimal("10"), "balanced-sell")
        exchangeOrderService.processQueuedOrder(sell.id)
        before = assertBalancedChange(before, BigDecimal.ZERO)

        walletService.withdrawToAccount(USER_ID, KRW_ID, BigDecimal("50000"))
        assertBalancedChange(before, BigDecimal("50000"))
        journalRepository.count() shouldBe 4L
        journalReconciliationService.audit().balanced shouldBe true

        exchangeOrderRepository.findAll().count { it.status == OrderStatus.SETTLED } shouldBe 2
        transactionRepository.count() shouldBe 4L
        exchangeLedgerRepository.count() shouldBe 2L
        jdbcTemplate.queryForObject("select count(*) from deposit_withdrawal", Long::class.java) shouldBe 2L
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
        assertThrows<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "insert into internal_account (code, currency_id, balance) values (?, ?, ?)",
                CompanyAccount.KRW_ACCOUNT.name,
                KRW_ID,
                BigDecimal.ZERO
            )
        }
        val accountId = accountRepository.findByBankIdAndAccountNum(1L, USER_ACCOUNT_NUM)!!.id!!
        assertThrows<DataIntegrityViolationException> {
            jdbcTemplate.update(
                "insert into simulated_bank_balance (account_id, balance) values (?, ?)",
                accountId,
                BigDecimal.ZERO
            )
        }
    }

    @Test
    fun `real MySQL schema keeps precise non-null balances and no legacy account balance`() {
        assumeTrue(System.getenv("RUN_REAL_INFRA_TESTS") == "true")

        jdbcTemplate.dataSource!!.connection.use { connection ->
            val metadata = connection.metaData
            for (table in listOf("wallet", "internal_account", "simulated_bank_balance")) {
                metadata.getColumns(connection.catalog, null, table, "balance").use { columns ->
                    check(columns.next()) { "Missing $table.balance" }
                    columns.getInt("DATA_TYPE") shouldBe Types.DECIMAL
                    columns.getInt("COLUMN_SIZE") shouldBe 24
                    columns.getInt("DECIMAL_DIGITS") shouldBe 8
                    columns.getInt("NULLABLE") shouldBe DatabaseMetaData.columnNoNulls
                    columns.next() shouldBe false
                }
            }
            metadata.getColumns(connection.catalog, null, "account", "balance").use { columns ->
                columns.next() shouldBe false
            }
            metadata.getColumns(connection.catalog, null, "wallet", "is_connected").use { columns ->
                columns.next() shouldBe false
            }
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

    private fun insertExchangeUser(userId: Long, krwBalance: BigDecimal, usdBalance: BigDecimal) {
        jdbcTemplate.update(
            "insert into $quotedUserTable (id, device, password, name, phone, type, is_active, created_at) " +
                "values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
            userId,
            "exchange-test-$userId",
            "password",
            "exchange-test-$userId",
            "010${(50000000L + userId).toString().padStart(8, '0')}",
            "GENERAL",
            true
        )
        jdbcTemplate.update(
            "insert into wallet (user_id, currency_id, balance) values (?, ?, ?)",
            userId, KRW_ID, krwBalance
        )
        jdbcTemplate.update(
            "insert into wallet (user_id, currency_id, balance) values (?, ?, ?)",
            userId, USD_ID, usdBalance
        )
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

    private data class BalanceSnapshot(
        val walletKrw: BigDecimal,
        val walletUsd: BigDecimal,
        val cashKrw: BigDecimal,
        val cashUsd: BigDecimal,
        val feeRevenueKrw: BigDecimal,
        val externalBankKrw: BigDecimal
    )

    private fun balanceSnapshot() = BalanceSnapshot(
        walletBalance(KRW_ID),
        walletBalance(USD_ID),
        companyBalance(CompanyAccount.KRW_ACCOUNT),
        companyBalance(CompanyAccount.USD_ACCOUNT),
        companyBalance(CompanyAccount.COMMISSION_ACCOUNT),
        simulatedBankBalance()
    )

    private fun assertBalancedChange(before: BalanceSnapshot, expectedBankDelta: BigDecimal): BalanceSnapshot {
        val after = balanceSnapshot()
        val walletKrwDelta = after.walletKrw.subtract(before.walletKrw)
        val feeDelta = after.feeRevenueKrw.subtract(before.feeRevenueKrw)
        after.cashKrw.subtract(before.cashKrw).compareTo(walletKrwDelta.add(feeDelta)) shouldBe 0
        after.cashUsd.subtract(before.cashUsd)
            .compareTo(after.walletUsd.subtract(before.walletUsd)) shouldBe 0
        after.externalBankKrw.subtract(before.externalBankKrw).compareTo(expectedBankDelta) shouldBe 0
        return after
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
        fun fixedExchangeService(): FixedExchangeService = FixedExchangeService()

        @Bean
        @Primary
        fun controllableLedgerService(
            exchangeLedgerRepository: ExchangeLedgerRepository
        ): ControllableLedgerService = ControllableLedgerService(exchangeLedgerRepository)
    }

    class FixedExchangeService : ExchangeService {
        @Volatile var buyRate: BigDecimal = RATE
        @Volatile var sellRate: BigDecimal = RATE
        @Volatile var buyBaseRate: BigDecimal = RATE
        @Volatile var sellBaseRate: BigDecimal = RATE

        override fun getBestBuyRate(currencyCode: String) = bestRate(currencyCode, ExchangeType.BUY, buyRate)
        override fun getBestSellRate(currencyCode: String) = bestRate(currencyCode, ExchangeType.SELL, sellRate)
        override fun getBestBuyBaseRate(currencyCode: String) = bestRate(currencyCode, ExchangeType.BUY, buyBaseRate)
        override fun getBestSellBaseRate(currencyCode: String) = bestRate(currencyCode, ExchangeType.SELL, sellBaseRate)

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

        private fun bestRate(currencyCode: String, type: ExchangeType, rate: BigDecimal) = BestExchangeRateDto(
            bankId = 1L,
            currencyId = if (currencyCode == "JPY") JPY_ID else USD_ID,
            bestRate = rate,
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
        private const val SECOND_USER_ID = 11L
        private const val KRW_ID = 1L
        private const val USD_ID = 2L
        private const val JPY_ID = 3L
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
