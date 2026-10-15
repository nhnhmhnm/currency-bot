package org.example.backend.exchange.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.example.backend.common.util.RedisCompletionQueue
import org.example.backend.common.util.StreamDelivery
import org.example.backend.enums.OutboxEventType
import org.example.backend.exchange.domain.OutboxEvent
import org.example.backend.exchange.repository.CompletedOrderViewRepository
import org.example.backend.exchange.repository.OutboxEventRepository

class CompletedOrderWorkerTest : FunSpec({
    val queue = mockk<RedisCompletionQueue>()
    val projector = mockk<CompletedOrderProjectionService>()
    val worker = CompletedOrderWorker(queue, projector, mockk<OutboxEventRepository>(), mockk<CompletedOrderViewRepository>())
    val delivery = StreamDelivery("1-0", 7L, "consumer-a")

    beforeTest { clearAllMocks() }

    test("projection failure leaves the completion pending without affecting settlement") {
        every { queue.recoverExpired(any()) } returns emptyList()
        every { queue.claim() } returnsMany listOf(delivery, null)
        every { projector.apply(7L) } throws IllegalStateException("read model unavailable")

        worker.processQueue()

        verify(exactly = 0) { queue.acknowledge(any()) }
    }

    test("completion is acknowledged only after projection succeeds") {
        every { queue.recoverExpired(any()) } returns emptyList()
        every { queue.claim() } returnsMany listOf(delivery, null)
        every { projector.apply(7L) } returns Unit
        every { queue.acknowledge(delivery) } returns true

        worker.processQueue()

        verify(exactly = 1) { projector.apply(7L) }
        verify(exactly = 1) { queue.acknowledge(delivery) }
    }

    test("recovery scans one page per run and wraps after the last page") {
        val events = mockk<OutboxEventRepository>()
        val views = mockk<CompletedOrderViewRepository>()
        val recovery = CompletedOrderWorker(queue, projector, events, views)
        val firstPage = (1L..100L).map { OutboxEvent(id = it, aggregateId = it, eventType = OutboxEventType.ORDER_COMPLETED) }
        val lastPage = listOf(OutboxEvent(id = 101L, aggregateId = 101L, eventType = OutboxEventType.ORDER_COMPLETED))
        every { events.findTop100ByEventTypeAndIdGreaterThanOrderByIdAsc(OutboxEventType.ORDER_COMPLETED, 0L) } returnsMany listOf(firstPage, firstPage)
        every { events.findTop100ByEventTypeAndIdGreaterThanOrderByIdAsc(OutboxEventType.ORDER_COMPLETED, 100L) } returns lastPage
        every { events.findTop100ByEventTypeAndIdGreaterThanOrderByIdAsc(OutboxEventType.ORDER_COMPLETED, 101L) } returns emptyList()
        every { views.existsById(any()) } answers { firstArg<Long>() != 101L }
        every { queue.ensureQueued(101L) } returns true

        repeat(4) { recovery.recoverUnprojected() }

        verify(exactly = 2) { events.findTop100ByEventTypeAndIdGreaterThanOrderByIdAsc(OutboxEventType.ORDER_COMPLETED, 0L) }
        verify(exactly = 1) { events.findTop100ByEventTypeAndIdGreaterThanOrderByIdAsc(OutboxEventType.ORDER_COMPLETED, 100L) }
        verify(exactly = 1) { queue.ensureQueued(101L) }
    }
})
