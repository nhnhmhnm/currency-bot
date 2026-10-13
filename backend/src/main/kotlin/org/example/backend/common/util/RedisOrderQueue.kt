package org.example.backend.common.util

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class RedisOrderQueue(
    private val redisTemplate: StringRedisTemplate
) {
    fun enqueue(eventId: Long, orderId: Long): Boolean {
        val token = encode(eventId, orderId)
        return redisTemplate.execute(
            ENQUEUE_SCRIPT,
            listOf(PENDING_KEY, deduplicationKey(eventId)),
            token
        ) == 1L
    }

    fun claim(): OrderQueueMessage? {
        val leaseId = "${UUID.randomUUID()}:${Instant.now().toEpochMilli()}"
        val token: String? = redisTemplate.execute(
            CLAIM_SCRIPT,
            listOf(PENDING_KEY, PROCESSING_KEY, LEASE_KEY),
            leaseId
        )
        if (token == null) return null

        return decode(token, leaseId)
    }

    fun acknowledge(message: OrderQueueMessage): Boolean {
        return redisTemplate.execute(
            ACKNOWLEDGE_SCRIPT,
            listOf(PROCESSING_KEY, LEASE_KEY),
            message.token,
            message.leaseId
        ) == 1L
    }

    fun defer(message: OrderQueueMessage, dueAt: Instant): Boolean {
        return redisTemplate.execute(
            DEFER_SCRIPT,
            listOf(PROCESSING_KEY, LEASE_KEY, DELAYED_KEY),
            message.token,
            message.leaseId,
            dueAt.toEpochMilli().toString()
        ) == 1L
    }

    fun releaseDue(now: Instant = Instant.now()): Long = redisTemplate.execute(
        RELEASE_DUE_SCRIPT,
        listOf(DELAYED_KEY, PENDING_KEY),
        now.toEpochMilli().toString()
    )

    fun ensureQueued(orderId: Long): Boolean = redisTemplate.execute(
        ENSURE_QUEUED_SCRIPT,
        listOf(PENDING_KEY, PROCESSING_KEY, DELAYED_KEY),
        encode(0, orderId)
    ) == 1L

    fun recoverExpired(leaseTimeout: Duration): List<OrderQueueMessage> {
        val cutoff = Instant.now().minus(leaseTimeout).toEpochMilli()
        val processingMessages = redisTemplate.opsForList().range(PROCESSING_KEY, 0, -1).orEmpty()

        return processingMessages.mapNotNull { token ->
            val leaseId = redisTemplate.opsForHash<String, String>().get(LEASE_KEY, token)
                ?: return@mapNotNull null
            val leasedAt = leaseId.substringAfterLast(':').toLongOrNull() ?: return@mapNotNull null
            if (leasedAt > cutoff) {
                return@mapNotNull null
            }

            decode(token, leaseId)
        }
    }

    private fun encode(eventId: Long, orderId: Long): String = "$eventId:$orderId"

    private fun deduplicationKey(eventId: Long): String = "$DEDUPLICATION_PREFIX:$eventId"

    private fun decode(token: String, leaseId: String): OrderQueueMessage? {
        val parts = token.split(':', limit = 2)
        if (parts.size != 2 || parts[0].toLongOrNull() == null) return null
        val orderId = parts[1].toLongOrNull() ?: return null
        return OrderQueueMessage(token = token, orderId = orderId, leaseId = leaseId)
    }

    companion object {
        private const val PENDING_KEY = "order:queue:pending"
        private const val PROCESSING_KEY = "order:queue:processing"
        private const val LEASE_KEY = "order:queue:lease"
        private const val DELAYED_KEY = "order:queue:delayed"
        private const val DEDUPLICATION_PREFIX = "order:queue:published"
        private const val DEDUPLICATION_TTL_SECONDS = 604800

        private val ENQUEUE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('SET', KEYS[2], '1', 'NX', 'EX', $DEDUPLICATION_TTL_SECONDS) then
                redis.call('RPUSH', KEYS[1], ARGV[1])
                return 1
            end
            return 0
            """.trimIndent(),
            Long::class.javaObjectType
        )
        private val CLAIM_SCRIPT = DefaultRedisScript(
            """
            local value = redis.call('LPOP', KEYS[1])
            if value then
                redis.call('RPUSH', KEYS[2], value)
                redis.call('HSET', KEYS[3], value, ARGV[1])
            end
            return value
            """.trimIndent(),
            String::class.java
        )
        private val ACKNOWLEDGE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('HGET', KEYS[2], ARGV[1]) ~= ARGV[2] then return 0 end
            redis.call('LREM', KEYS[1], 1, ARGV[1])
            redis.call('HDEL', KEYS[2], ARGV[1])
            return 1
            """.trimIndent(),
            Long::class.javaObjectType
        )
        private val DEFER_SCRIPT = DefaultRedisScript(
            """
            if redis.call('HGET', KEYS[2], ARGV[1]) ~= ARGV[2] then return 0 end
            redis.call('LREM', KEYS[1], 1, ARGV[1])
            redis.call('HDEL', KEYS[2], ARGV[1])
            redis.call('ZADD', KEYS[3], ARGV[3], ARGV[1])
            return 1
            """.trimIndent(),
            Long::class.javaObjectType
        )
        private val RELEASE_DUE_SCRIPT = DefaultRedisScript(
            """
            local tokens = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, 100)
            for _, token in ipairs(tokens) do
                redis.call('ZREM', KEYS[1], token)
                redis.call('RPUSH', KEYS[2], token)
            end
            return #tokens
            """.trimIndent(),
            Long::class.javaObjectType
        )
        private val ENSURE_QUEUED_SCRIPT = DefaultRedisScript(
            """
            if redis.call('LPOS', KEYS[1], ARGV[1]) or
               redis.call('LPOS', KEYS[2], ARGV[1]) or
               redis.call('ZSCORE', KEYS[3], ARGV[1]) then return 0 end
            redis.call('RPUSH', KEYS[1], ARGV[1])
            return 1
            """.trimIndent(),
            Long::class.javaObjectType
        )
    }
}
