package org.example.backend.exchange.service

import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.common.util.RedisCompletionQueue
import org.example.backend.enums.OutboxEventStatus
import org.example.backend.enums.OutboxEventType
import org.example.backend.exchange.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class OutboxEventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val orderQueue: RedisOrderQueue,
    private val completionQueue: RedisCompletionQueue
) {
    private val log = LoggerFactory.getLogger(OutboxEventPublisher::class.java)

    @Scheduled(fixedDelayString = "\${app.outbox.publish-delay-ms:500}")
    @Transactional
    fun publishPendingOrderEvents() {
        publish(OutboxEventType.ORDER_ACCEPTED)
        publish(OutboxEventType.ORDER_COMPLETED)
    }

    private fun publish(type: OutboxEventType) {
        outboxEventRepository.findTop100ByStatusAndEventTypeOrderById(OutboxEventStatus.PENDING, type).forEach { event ->
            try {
                when (type) {
                    OutboxEventType.ORDER_ACCEPTED -> orderQueue.enqueue(event.id!!, event.aggregateId)
                    OutboxEventType.ORDER_COMPLETED -> completionQueue.enqueue(event.id!!)
                }
                event.status = OutboxEventStatus.PUBLISHED
                event.publishAttempts += 1
                event.lastError = null
                event.publishedAt = LocalDateTime.now()
            } catch (exception: Exception) {
                event.publishAttempts += 1
                event.lastError = (exception.message ?: exception::class.simpleName.orEmpty()).take(255)
                log.warn("outbox publish failed: eventId={}, type={}", event.id, type, exception)
            }
        }
    }
}
