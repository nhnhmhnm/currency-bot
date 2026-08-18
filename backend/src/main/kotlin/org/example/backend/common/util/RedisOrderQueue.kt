package org.example.backend.common.util

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisOrderQueue(
    private val redisTemplate: StringRedisTemplate
) {
    private val queueKey = "order:queue"

    fun enqueue(orderId: Long) {
        redisTemplate.opsForList().rightPush(queueKey, orderId.toString())
    }

    fun dequeue(): Long? {
        return redisTemplate.opsForList().leftPop(queueKey)?.toLongOrNull()
    }
}
