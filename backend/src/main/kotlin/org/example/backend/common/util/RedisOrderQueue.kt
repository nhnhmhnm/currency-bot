package org.example.backend.common.util

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.example.backend.exchange.dto.ExchangeOrderRequest

@Component
class RedisOrderQueue(
    private val redisTemplate: StringRedisTemplate
) {
    private val QUEUE_KEY = "order:queue"
    private val mapper = jacksonObjectMapper()

    // 큐에 주문 요청을 추가
    fun enqueue(req: ExchangeOrderRequest) {
        val json = mapper.writeValueAsString(req)
        redisTemplate.opsForList().rightPush(QUEUE_KEY, json)
    }

    // 큐에서 주문 요청을 가져오고 제거
    fun dequeue(): ExchangeOrderRequest? {
        val json = redisTemplate.opsForList().leftPop(QUEUE_KEY) ?: return null
        return try {
            mapper.readValue(json, ExchangeOrderRequest::class.java)
        } catch (e: Exception) {
            // 파싱 실패 시 해당 항목은 스킵하고 다음으로 진행
            // logger.warn("Invalid order json. skip. json=$json", e)
            null
        }
    }
}
