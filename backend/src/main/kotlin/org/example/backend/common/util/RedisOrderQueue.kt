package org.example.backend.common.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.example.backend.exchange.dto.ExchangeOrderRequest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisOrderQueue(
    private val redisTemplate: StringRedisTemplate
) {
    private val orderQueueKey = "order:queue"
    private val mapper = jacksonObjectMapper()

    fun enqueue(orderRequest: ExchangeOrderRequest) {
        val orderRequestJson = mapper.writeValueAsString(orderRequest)
        redisTemplate.opsForList().rightPush(orderQueueKey, orderRequestJson)
    }

    fun dequeue(): ExchangeOrderRequest? {
        val orderRequestJson = redisTemplate.opsForList().leftPop(orderQueueKey) ?: return null
        return mapper.readValue(orderRequestJson, ExchangeOrderRequest::class.java)
    }
}
