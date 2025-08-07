package org.example.backend.common.util

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.example.backend.exchange.dto.ExchangeOrderRequest

@Component
class RedisOrderQueue(
    private val redisTemplate: StringRedisTemplate
) {
    private val QUEUE_KEY = "orderQueue"
    private val objectMapper = jacksonObjectMapper()

    fun enqueue(orderRequest: ExchangeOrderRequest) {
        val json = objectMapper.writeValueAsString(orderRequest)
        redisTemplate.opsForList().rightPush(QUEUE_KEY, json)
    }

    fun dequeue(): ExchangeOrderRequest? {
        val json = redisTemplate.opsForList().leftPop(QUEUE_KEY) ?: return null
        return objectMapper.readValue(json, ExchangeOrderRequest::class.java)
    }
}
