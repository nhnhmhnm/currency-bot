package org.example.backend.exchange.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.example.backend.enums.OutboxEventStatus
import org.example.backend.enums.OutboxEventType
import org.example.backend.exchange.domain.OutboxEvent
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.exchange.repository.OutboxEventRepository
import java.time.LocalDateTime

class ExchangeOperationsSnapshotServiceTest : FunSpec({
    test("snapshot reports backlog age and measured lock waits") {
        val orders = mockk<ExchangeOrderRepository>()
        val events = mockk<OutboxEventRepository>()
        every { orders.countByStatusIn(ExchangeOrderRepository.RESERVING_STATUSES) } returns 0L
        every { orders.findTop1ByStatusInOrderByRequestedAtAsc(ExchangeOrderRepository.RESERVING_STATUSES) } returns null
        every { events.countByStatus(OutboxEventStatus.PENDING) } returns 1L
        every { events.findTop1ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING) } returns
            OutboxEvent(id = 1L, aggregateId = 3L, eventType = OutboxEventType.ORDER_ACCEPTED,
                createdAt = LocalDateTime.now().minusSeconds(30))
        val service = ExchangeOperationsSnapshotService(orders, events)

        service.measureLockWait { 42 } shouldBe 42
        val snapshot = service.snapshot()

        snapshot.incompleteOrders shouldBe 0L
        snapshot.oldestIncompleteSeconds shouldBe 0L
        snapshot.pendingOutboxEvents shouldBe 1L
        snapshot.oldestOutboxSeconds.shouldBeGreaterThanOrEqual(30L)
        snapshot.lockWaitCount shouldBe 1L
        snapshot.lockWaitAverageMillis.shouldBeGreaterThanOrEqual(0.0)
        snapshot.lockWaitMaxMillis.shouldBeGreaterThanOrEqual(snapshot.lockWaitAverageMillis)
    }
})
