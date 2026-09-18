package org.example.backend.common.util

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

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
        val token: String? = redisTemplate.execute(
            CLAIM_SCRIPT,
            listOf(PENDING_KEY, PROCESSING_KEY, LEASE_KEY),
            Instant.now().toEpochMilli().toString()
        )
        if (token == null) return null

        return decode(token)
    }

    fun acknowledge(message: OrderQueueMessage) {
        redisTemplate.execute(
            ACKNOWLEDGE_SCRIPT,
            listOf(PROCESSING_KEY, LEASE_KEY, ATTEMPT_KEY),
            message.token
        )
    }

    fun reject(message: OrderQueueMessage, maxAttempts: Int): Int {
        val attempts: Long? = redisTemplate.execute(
            REJECT_SCRIPT,
            listOf(PROCESSING_KEY, LEASE_KEY, ATTEMPT_KEY, PENDING_KEY),
            message.token,
            maxAttempts.toString()
        )
        return attempts?.toInt() ?: maxAttempts
    }

    fun recoverExpired(maxAttempts: Int, leaseTimeout: Duration): List<OrderQueueMessage> {
        val cutoff = Instant.now().minus(leaseTimeout).toEpochMilli()
        val processingMessages = redisTemplate.opsForList().range(PROCESSING_KEY, 0, -1).orEmpty()

        return processingMessages.mapNotNull { token ->
            val leasedAt = redisTemplate.opsForHash<String, String>().get(LEASE_KEY, token)?.toLongOrNull()
            if (leasedAt != null && leasedAt > cutoff) {
                return@mapNotNull null
            }

            val message = decode(token) ?: return@mapNotNull null
            val attempts = reject(message, maxAttempts)
            message.takeIf { attempts >= maxAttempts }
        }
    }

    private fun encode(eventId: Long, orderId: Long): String = "$eventId:$orderId"

    private fun deduplicationKey(eventId: Long): String = "$DEDUPLICATION_PREFIX:$eventId"

    private fun decode(token: String): OrderQueueMessage? {
        val parts = token.split(':', limit = 2)
        if (parts.size != 2 || parts[0].toLongOrNull() == null) return null
        val orderId = parts[1].toLongOrNull() ?: return null
        return OrderQueueMessage(token = token, orderId = orderId)
    }

    companion object {
        private const val PENDING_KEY = "order:queue:pending"
        private const val PROCESSING_KEY = "order:queue:processing"
        private const val LEASE_KEY = "order:queue:lease"
        private const val ATTEMPT_KEY = "order:queue:attempt"
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
            redis.call('LREM', KEYS[1], 1, ARGV[1])
            redis.call('HDEL', KEYS[2], ARGV[1])
            redis.call('HDEL', KEYS[3], ARGV[1])
            return 1
            """.trimIndent(),
            Long::class.javaObjectType
        )
        private val REJECT_SCRIPT = DefaultRedisScript(
            """
            local attempts = redis.call('HINCRBY', KEYS[3], ARGV[1], 1)
            redis.call('LREM', KEYS[1], 1, ARGV[1])
            redis.call('HDEL', KEYS[2], ARGV[1])
            if attempts < tonumber(ARGV[2]) then
                redis.call('RPUSH', KEYS[4], ARGV[1])
            else
                redis.call('HDEL', KEYS[3], ARGV[1])
            end
            return attempts
            """.trimIndent(),
            Long::class.javaObjectType
        )
    }
}
