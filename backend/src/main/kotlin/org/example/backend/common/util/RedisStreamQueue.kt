package org.example.backend.common.util

import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class StreamDelivery(val recordId: String, val subjectId: Long, val consumer: String)

class RedisStreamQueue(
    private val redis: StringRedisTemplate,
    private val streamKey: String,
    private val group: String
) {
    private val delayedKey = "$streamKey:delayed"
    private val publishedPrefix = "$streamKey:published"

    fun enqueue(eventId: Long, subjectId: Long): Boolean = redis.execute(
        ENQUEUE,
        listOf(streamKey, "$publishedPrefix:$eventId"),
        subjectId.toString()
    ) == 1L

    fun claim(): StreamDelivery? {
        ensureGroup()
        // A delivery-specific consumer fences ACKs from a worker that lost its pending entry.
        val consumer = UUID.randomUUID().toString()
        val records = redis.opsForStream<String, String>().read(
            Consumer.from(group, consumer),
            StreamReadOptions.empty().count(1),
            StreamOffset.create(streamKey, ReadOffset.lastConsumed())
        ).orEmpty()
        if (records.isEmpty()) {
            redis.execute(DROP_CONSUMER, listOf(streamKey), group, consumer)
            return null
        }
        return records.firstOrNull()?.let { record ->
            val subjectId = record.value["subjectId"]?.toLongOrNull()
                ?: error("Invalid stream payload at ${record.id.value}")
            StreamDelivery(record.id.value, subjectId, consumer)
        }
    }

    fun acknowledge(delivery: StreamDelivery): Boolean = redis.execute(
        ACKNOWLEDGE,
        listOf(streamKey),
        group, delivery.recordId, delivery.consumer
    ) == 1L

    fun defer(delivery: StreamDelivery, dueAt: Instant): Boolean = redis.execute(
        DEFER,
        listOf(streamKey, delayedKey),
        group, delivery.recordId, delivery.consumer, delivery.subjectId.toString(), dueAt.toEpochMilli().toString()
    ) == 1L

    fun releaseDue(now: Instant = Instant.now()): Long = redis.execute(
        RELEASE_DUE,
        listOf(delayedKey, streamKey),
        now.toEpochMilli().toString()
    )

    fun ensureQueued(subjectId: Long): Boolean = redis.execute(
        ENSURE_QUEUED,
        listOf(streamKey, "$streamKey:recovery:$subjectId"),
        subjectId.toString()
    ) == 1L

    fun recoverExpired(idleTime: Duration): List<StreamDelivery> {
        ensureGroup()
        val streams = redis.opsForStream<String, String>()
        val minimumIdle = if (idleTime.isNegative) Duration.ZERO else idleTime
        return streams.pending(streamKey, group, Range.unbounded<String>(), 100).toList()
            .filter { it.elapsedTimeSinceLastDelivery >= minimumIdle }
            .flatMap { pending ->
                val consumer = UUID.randomUUID().toString()
                streams.claim(streamKey, group, consumer, minimumIdle, RecordId.of(pending.idAsString))
                    .mapNotNull { record ->
                        record.value["subjectId"]?.toLongOrNull()?.let {
                            StreamDelivery(record.id.value, it, consumer)
                        }
                    }
            }
    }

    private fun ensureGroup() {
        redis.execute(CREATE_GROUP, listOf(streamKey), group)
    }

    companion object {
        private val DROP_CONSUMER = DefaultRedisScript(
            "redis.call('XGROUP', 'DELCONSUMER', KEYS[1], ARGV[1], ARGV[2]); return 1",
            Long::class.javaObjectType
        )
        private val CREATE_GROUP = DefaultRedisScript(
            """
            local result = redis.pcall('XGROUP', 'CREATE', KEYS[1], ARGV[1], '0', 'MKSTREAM')
            if type(result) == 'table' and result.err then
                if string.find(result.err, 'BUSYGROUP') then return 0 end
                return redis.error_reply(result.err)
            end
            return 1
            """.trimIndent(), Long::class.javaObjectType
        )
        private val ENQUEUE = DefaultRedisScript(
            """
            if redis.call('SET', KEYS[2], '1', 'NX', 'EX', 604800) then
                redis.call('XADD', KEYS[1], '*', 'subjectId', ARGV[1])
                return 1
            end
            return 0
            """.trimIndent(), Long::class.javaObjectType
        )
        private val ACKNOWLEDGE = DefaultRedisScript(
            """
            local pending = redis.call('XPENDING', KEYS[1], ARGV[1], ARGV[2], ARGV[2], 1)
            if #pending ~= 1 or pending[1][2] ~= ARGV[3] then return 0 end
            redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
            redis.call('XDEL', KEYS[1], ARGV[2])
            redis.call('XGROUP', 'DELCONSUMER', KEYS[1], ARGV[1], ARGV[3])
            return 1
            """.trimIndent(), Long::class.javaObjectType
        )
        private val DEFER = DefaultRedisScript(
            """
            local pending = redis.call('XPENDING', KEYS[1], ARGV[1], ARGV[2], ARGV[2], 1)
            if #pending ~= 1 or pending[1][2] ~= ARGV[3] then return 0 end
            redis.call('ZADD', KEYS[2], ARGV[5], ARGV[4])
            redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
            redis.call('XDEL', KEYS[1], ARGV[2])
            redis.call('XGROUP', 'DELCONSUMER', KEYS[1], ARGV[1], ARGV[3])
            return 1
            """.trimIndent(), Long::class.javaObjectType
        )
        private val RELEASE_DUE = DefaultRedisScript(
            """
            local subjects = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, 100)
            for _, subject in ipairs(subjects) do
                redis.call('XADD', KEYS[2], '*', 'subjectId', subject)
                redis.call('ZREM', KEYS[1], subject)
            end
            return #subjects
            """.trimIndent(), Long::class.javaObjectType
        )
        private val ENSURE_QUEUED = DefaultRedisScript(
            """
            if redis.call('SET', KEYS[2], '1', 'NX', 'EX', 60) then
                redis.call('XADD', KEYS[1], '*', 'subjectId', ARGV[1])
                return 1
            end
            return 0
            """.trimIndent(), Long::class.javaObjectType
        )
    }
}
