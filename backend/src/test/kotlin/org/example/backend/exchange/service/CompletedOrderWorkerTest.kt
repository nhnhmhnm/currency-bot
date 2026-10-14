package org.example.backend.exchange.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.example.backend.common.util.RedisCompletionQueue
import org.example.backend.common.util.StreamDelivery
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
})
