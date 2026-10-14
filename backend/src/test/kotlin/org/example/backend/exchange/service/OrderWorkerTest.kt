package org.example.backend.exchange.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.example.backend.common.util.OrderQueueMessage
import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.enums.PublicOrderStatus
import org.example.backend.enums.OrderStatus
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.dto.ExchangeOrderResponse
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.springframework.dao.CannotAcquireLockException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

class OrderWorkerTest : FunSpec({
    val queue = mockk<RedisOrderQueue>()
    val exchangeOrderService = mockk<ExchangeOrderService>()
    val exchangeOrderRepository = mockk<ExchangeOrderRepository>()
    val worker = OrderWorker(queue, exchangeOrderService, exchangeOrderRepository)
    val message = OrderQueueMessage("1:10", 10L, "lease-1")
    val due = Instant.now().plusSeconds(5)

    beforeTest {
        clearAllMocks()
        every { queue.releaseDue(any()) } returns 0L
        every { queue.recoverExpired(any()) } returns emptyList()
        every { queue.claim() } returnsMany listOf(message, null)
        every { queue.acknowledge(message) } returns true
    }

    test("ACK follows settlement commit") {
        every { exchangeOrderService.processQueuedOrder(10L) } returns completedOrder(10L)

        worker.processQueue()

        verifyOrder {
            exchangeOrderService.processQueuedOrder(10L)
            queue.acknowledge(message)
        }
    }

    test("ACK failure never records settlement failure") {
        every { exchangeOrderService.processQueuedOrder(10L) } returns completedOrder(10L)
        every { queue.acknowledge(message) } throws IllegalStateException("Redis unavailable")

        worker.processQueue()

        verify(exactly = 1) { queue.acknowledge(message) }
        verify(exactly = 0) { exchangeOrderService.recordProcessingFailure(any(), any(), any(), any()) }
    }

    test("transient settlement failure persists retry before deferring message") {
        every { exchangeOrderService.processQueuedOrder(10L) } throws IllegalStateException("temporary")
        every { exchangeOrderService.recordProcessingFailure(10L, "lease-1", "temporary", 3) } returns
            OrderRetryDecision(false, due)
        every { queue.defer(message, due) } returns true

        worker.processQueue()

        verifyOrder {
            exchangeOrderService.processQueuedOrder(10L)
            exchangeOrderService.recordProcessingFailure(10L, "lease-1", "temporary", 3)
            queue.defer(message, due)
        }
        verify(exactly = 0) { queue.acknowledge(any()) }
    }

    test("deadlock schedules a fresh worker settlement attempt") {
        every { exchangeOrderService.processQueuedOrder(10L) } throws CannotAcquireLockException("deadlock")
        every { exchangeOrderService.recordProcessingFailure(10L, "lease-1", "deadlock", 3) } returns
            OrderRetryDecision(false, due)
        every { queue.defer(message, due) } returns true

        worker.processQueue()

        verifyOrder {
            exchangeOrderService.processQueuedOrder(10L)
            exchangeOrderService.recordProcessingFailure(10L, "lease-1", "deadlock", 3)
            queue.defer(message, due)
        }
        verify(exactly = 0) { queue.acknowledge(any()) }
    }

    test("failed retry persistence leaves message under lease for recovery") {
        every { exchangeOrderService.processQueuedOrder(10L) } throws IllegalStateException("temporary")
        every { exchangeOrderService.recordProcessingFailure(10L, "lease-1", "temporary", 3) } throws
            IllegalStateException("DB unavailable")

        worker.processQueue()

        verify(exactly = 0) { queue.defer(any(), any()) }
        verify(exactly = 0) { queue.acknowledge(any()) }
    }

    test("final failure is committed before ACK") {
        every { exchangeOrderService.processQueuedOrder(10L) } throws IllegalStateException("persistent")
        every { exchangeOrderService.recordProcessingFailure(10L, "lease-1", "persistent", 3) } returns
            OrderRetryDecision(true)

        worker.processQueue()

        verifyOrder {
            exchangeOrderService.recordProcessingFailure(10L, "lease-1", "persistent", 3)
            queue.acknowledge(message)
        }
    }

    test("not-yet-due retry is deferred without counting a failure") {
        every { exchangeOrderService.processQueuedOrder(10L) } throws OrderRetryNotDueException(due)
        every { queue.defer(message, due) } returns true

        worker.processQueue()

        verify(exactly = 1) { queue.defer(message, due) }
        verify(exactly = 0) { exchangeOrderService.recordProcessingFailure(any(), any(), any(), any()) }
    }

    test("expired lease records a retry before releasing the message") {
        every { queue.recoverExpired(any()) } returns listOf(message)
        every { queue.claim() } returns null
        every { exchangeOrderService.recordProcessingFailure(10L, "lease-1", "worker lease expired", 3) } returns
            OrderRetryDecision(false, due)
        every { queue.defer(message, due) } returns true

        worker.processQueue()

        verifyOrder {
            exchangeOrderService.recordProcessingFailure(10L, "lease-1", "worker lease expired", 3)
            queue.defer(message, due)
        }
    }

    test("old unfinished DB order is enqueued for recovery") {
        val order = ExchangeOrder(
            id = 10L,
            userId = 1L,
            bankId = 0L,
            fromCurrencyId = 1L,
            toCurrencyId = 2L,
            fromAmount = BigDecimal("100000"),
            toAmount = BigDecimal.ZERO,
            exchangeRate = BigDecimal.ZERO,
            status = OrderStatus.QUEUED
        )
        every {
            exchangeOrderRepository.findTop100ByStatusInAndRequestedAtBeforeAndIdGreaterThanOrderByIdAsc(
                any(), any(), 0L
            )
        } returns listOf(order)
        every { queue.ensureQueued(10L) } returns true

        worker.recoverIncompleteOrders()

        verify(exactly = 1) { queue.ensureQueued(10L) }
    }
})

private fun completedOrder(orderId: Long) = ExchangeOrderResponse(
    id = orderId,
    userId = 1L,
    bankId = 1L,
    fromCurrencyId = 1L,
    toCurrencyId = 2L,
    fromAmount = BigDecimal("100000"),
    toAmount = BigDecimal("76.92"),
    exchangeRate = BigDecimal("1300"),
    status = PublicOrderStatus.COMPLETED,
    requestedAt = LocalDateTime.now()
)
