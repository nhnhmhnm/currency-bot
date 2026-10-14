package org.example.backend.common.util

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisCompletionQueue(redis: StringRedisTemplate) {
    private val stream = RedisStreamQueue(redis, "order:completion:stream", "order-read-model")

    fun enqueue(eventId: Long): Boolean = stream.enqueue(eventId, eventId)
    fun ensureQueued(eventId: Long): Boolean = stream.ensureQueued(eventId)
    fun claim(): StreamDelivery? = stream.claim()
    fun acknowledge(delivery: StreamDelivery): Boolean = stream.acknowledge(delivery)
    fun recoverExpired(idleTime: Duration): List<StreamDelivery> = stream.recoverExpired(idleTime)
}
