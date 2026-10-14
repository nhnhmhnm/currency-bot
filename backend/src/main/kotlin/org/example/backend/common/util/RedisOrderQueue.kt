package org.example.backend.common.util

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class RedisOrderQueue(redis: StringRedisTemplate) {
    private val stream = RedisStreamQueue(redis, "order:queue:stream", "order-settlement")

    fun enqueue(eventId: Long, orderId: Long): Boolean = stream.enqueue(eventId, orderId)
    fun claim(): OrderQueueMessage? = stream.claim()?.toOrderMessage()
    fun acknowledge(message: OrderQueueMessage): Boolean = stream.acknowledge(message.toDelivery())
    fun defer(message: OrderQueueMessage, dueAt: Instant): Boolean = stream.defer(message.toDelivery(), dueAt)
    fun releaseDue(now: Instant = Instant.now()): Long = stream.releaseDue(now)
    fun ensureQueued(orderId: Long): Boolean = stream.ensureQueued(orderId)
    fun recoverExpired(leaseTimeout: Duration): List<OrderQueueMessage> =
        stream.recoverExpired(leaseTimeout).map { it.toOrderMessage() }

    private fun StreamDelivery.toOrderMessage() = OrderQueueMessage(recordId, subjectId, consumer)
    private fun OrderQueueMessage.toDelivery() = StreamDelivery(token, orderId, leaseId)
}
