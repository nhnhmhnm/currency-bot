package org.example.backend.exchange.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.example.backend.common.util.OrderQueueMessage
import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.enums.PublicOrderStatus
import org.example.backend.exchange.dto.ExchangeOrderResponse
import java.math.BigDecimal
import java.time.LocalDateTime

class OrderWorkerTest : FunSpec({
    val queue = mockk<RedisOrderQueue>()
    val exchangeOrderService = mockk<ExchangeOrderService>()
    val worker = OrderWorker(queue, exchangeOrderService)

    beforeTest {
        clearAllMocks()
        every { queue.recoverExpired(any(), any()) } returns emptyList()
    }

    test("acknowledges a queue message only after the order is processed") {
        val message = OrderQueueMessage(token = "1:10", orderId = 10L)
        every { queue.claim() } returnsMany listOf(message, null)
        every { exchangeOrderService.processQueuedOrder(10L) } returns completedOrder(10L)
        every { queue.acknowledge(message) } just runs

        worker.processQueue()

        verifyOrder {
            exchangeOrderService.processQueuedOrder(10L)
            queue.acknowledge(message)
        }
    }

    test("requeues a transient failure without marking the order failed") {
        val message = OrderQueueMessage(token = "2:20", orderId = 20L)
        every { queue.claim() } returns message
        every { exchangeOrderService.processQueuedOrder(20L) } throws IllegalStateException("temporary")
        every { queue.reject(message, 3) } returns 1

        worker.processQueue()

        verify(exactly = 1) { queue.reject(message, 3) }
        verify(exactly = 0) { exchangeOrderService.markFailed(any(), any(), any()) }
    }

    test("marks an order failed after the final retry") {
        val message = OrderQueueMessage(token = "3:30", orderId = 30L)
        every { queue.claim() } returnsMany listOf(message, null)
        every { exchangeOrderService.processQueuedOrder(30L) } throws IllegalStateException("persistent")
        every { queue.reject(message, 3) } returns 3
        every { exchangeOrderService.markFailed(30L, "ORDER_WORKER", "persistent") } just runs

        worker.processQueue()

        verify(exactly = 1) { exchangeOrderService.markFailed(30L, "ORDER_WORKER", "persistent") }
        verify(exactly = 0) { queue.acknowledge(any()) }
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
