package org.example.backend.exchange.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.common.util.RedisCompletionQueue
import org.example.backend.enums.OutboxEventStatus
import org.example.backend.enums.OutboxEventType
import org.example.backend.exchange.domain.OutboxEvent
import org.example.backend.exchange.repository.OutboxEventRepository

class OutboxEventPublisherTest : FunSpec({
    val repository = mockk<OutboxEventRepository>()
    val queue = mockk<RedisOrderQueue>()
    val completionQueue = mockk<RedisCompletionQueue>()
    val publisher = OutboxEventPublisher(repository, queue, completionQueue)

    beforeTest {
        clearAllMocks()
        every { repository.findTop100ByStatusAndEventTypeOrderById(OutboxEventStatus.PENDING, OutboxEventType.ORDER_COMPLETED) } returns emptyList()
    }

    test("publishes completion separately from settlement queue") {
        val event = OutboxEvent(id = 3L, aggregateId = 20L, eventType = OutboxEventType.ORDER_COMPLETED)
        every { repository.findTop100ByStatusAndEventTypeOrderById(OutboxEventStatus.PENDING, OutboxEventType.ORDER_ACCEPTED) } returns emptyList()
        every { repository.findTop100ByStatusAndEventTypeOrderById(OutboxEventStatus.PENDING, OutboxEventType.ORDER_COMPLETED) } returns listOf(event)
        every { completionQueue.enqueue(3L) } returns true

        publisher.publishPendingOrderEvents()

        event.status shouldBe OutboxEventStatus.PUBLISHED
        verify(exactly = 1) { completionQueue.enqueue(3L) }
        verify(exactly = 0) { queue.enqueue(any(), any()) }
    }

    test("marks an outbox event published after Redis accepts it") {
        val event = OutboxEvent(id = 1L, aggregateId = 10L, eventType = OutboxEventType.ORDER_ACCEPTED)
        every {
            repository.findTop100ByStatusAndEventTypeOrderById(
                OutboxEventStatus.PENDING,
                OutboxEventType.ORDER_ACCEPTED
            )
        } returns listOf(event)
        every { queue.enqueue(1L, 10L) } returns true

        publisher.publishPendingOrderEvents()

        event.status shouldBe OutboxEventStatus.PUBLISHED
        event.publishAttempts shouldBe 1
        verify(exactly = 1) { queue.enqueue(1L, 10L) }
    }

    test("keeps an outbox event pending when Redis publishing fails") {
        val event = OutboxEvent(id = 2L, aggregateId = 20L, eventType = OutboxEventType.ORDER_ACCEPTED)
        every {
            repository.findTop100ByStatusAndEventTypeOrderById(
                OutboxEventStatus.PENDING,
                OutboxEventType.ORDER_ACCEPTED
            )
        } returns listOf(event)
        every { queue.enqueue(2L, 20L) } throws IllegalStateException("redis unavailable")

        publisher.publishPendingOrderEvents()

        event.status shouldBe OutboxEventStatus.PENDING
        event.publishAttempts shouldBe 1
        event.lastError shouldBe "redis unavailable"
    }
})
