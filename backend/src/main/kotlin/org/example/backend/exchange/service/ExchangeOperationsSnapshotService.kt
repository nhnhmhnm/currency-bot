package org.example.backend.exchange.service

import org.example.backend.enums.OutboxEventStatus
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.exchange.repository.OutboxEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

data class ExchangeOperationsSnapshot(
    val incompleteOrders: Long,
    val oldestIncompleteSeconds: Long,
    val pendingOutboxEvents: Long,
    val oldestOutboxSeconds: Long,
    val lockWaitCount: Long,
    val lockWaitAverageMillis: Double,
    val lockWaitMaxMillis: Double
)

@Service
class ExchangeOperationsSnapshotService(
    private val orders: ExchangeOrderRepository,
    private val events: OutboxEventRepository
) {
    private val lockWaitCount = AtomicLong()
    private val lockWaitTotalNanos = AtomicLong()
    private val lockWaitMaxNanos = AtomicLong()

    fun <T> measureLockWait(action: () -> T): T {
        val started = System.nanoTime()
        try {
            return action()
        } finally {
            val elapsed = (System.nanoTime() - started).coerceAtLeast(0)
            lockWaitCount.incrementAndGet()
            lockWaitTotalNanos.addAndGet(elapsed)
            lockWaitMaxNanos.accumulateAndGet(elapsed, ::maxOf)
        }
    }

    @Transactional(readOnly = true)
    fun snapshot(): ExchangeOperationsSnapshot {
        val now = LocalDateTime.now()
        val statuses = ExchangeOrderRepository.RESERVING_STATUSES
        val count = lockWaitCount.get()
        return ExchangeOperationsSnapshot(
            incompleteOrders = orders.countByStatusIn(statuses),
            oldestIncompleteSeconds = ageSeconds(orders.findTop1ByStatusInOrderByRequestedAtAsc(statuses)?.requestedAt, now),
            pendingOutboxEvents = events.countByStatus(OutboxEventStatus.PENDING),
            oldestOutboxSeconds = ageSeconds(events.findTop1ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING)?.createdAt, now),
            lockWaitCount = count,
            lockWaitAverageMillis = if (count == 0L) 0.0 else lockWaitTotalNanos.get().toDouble() / count / 1_000_000,
            lockWaitMaxMillis = lockWaitMaxNanos.get().toDouble() / 1_000_000
        )
    }

    private fun ageSeconds(since: LocalDateTime?, now: LocalDateTime): Long =
        since?.let { Duration.between(it, now).seconds.coerceAtLeast(0) } ?: 0
}
