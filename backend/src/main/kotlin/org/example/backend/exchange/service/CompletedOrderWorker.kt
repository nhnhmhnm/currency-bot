package org.example.backend.exchange.service

import org.example.backend.common.util.RedisCompletionQueue
import org.example.backend.common.util.StreamDelivery
import org.example.backend.enums.OutboxEventType
import org.example.backend.exchange.repository.CompletedOrderViewRepository
import org.example.backend.exchange.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class CompletedOrderWorker(
    private val queue: RedisCompletionQueue,
    private val projector: CompletedOrderProjectionService,
    private val events: OutboxEventRepository,
    private val views: CompletedOrderViewRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var lastScannedEventId = 0L

    @Scheduled(fixedDelayString = "\${app.completion.worker-delay-ms:1000}")
    fun processQueue() {
        queue.recoverExpired(Duration.ofSeconds(30)).forEach(::process)
        repeat(100) {
            val delivery = queue.claim() ?: return
            process(delivery)
        }
    }

    @Scheduled(fixedDelayString = "\${app.completion.recovery-delay-ms:30000}")
    fun recoverUnprojected() {
        val batch = events.findTop100ByEventTypeAndIdGreaterThanOrderByIdAsc(
            OutboxEventType.ORDER_COMPLETED, lastScannedEventId
        )
        batch.filterNot { views.existsById(it.aggregateId) }.forEach { queue.ensureQueued(it.id!!) }
        lastScannedEventId = batch.lastOrNull()?.id ?: 0L
    }

    private fun process(delivery: StreamDelivery) {
        try {
            projector.apply(delivery.subjectId)
            if (!queue.acknowledge(delivery)) log.warn("completion ACK skipped after ownership change: eventId={}", delivery.subjectId)
        } catch (failure: Exception) {
            log.error("completion projection failed: eventId={}", delivery.subjectId, failure)
        }
    }
}
