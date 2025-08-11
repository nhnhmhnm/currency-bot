package org.example.backend.common.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.example.backend.enums.OrderType
import org.example.backend.exchange.dto.ExchangeOrderRequest
import java.math.BigDecimal

class RedisOrderQueueTest : BehaviorSpec({

    val redisOrderQueue = mockk<RedisOrderQueue>(relaxed = true)

    Given("사용자가 매수 주문 요청을 보냈을 때") {
        val request = ExchangeOrderRequest(
            userId = 1L,
            type = OrderType.BUY,
            currencyCode = "USD",
            amount = BigDecimal("100.00")
        )

        When("enqueue 메서드를 호출하면") {
            every { redisOrderQueue.enqueue(request) } returns Unit
            redisOrderQueue.enqueue(request)

            Then("Redis 큐에 요청이 저장되어야 한다") {
                verify { redisOrderQueue.enqueue(request) }
            }
        }
    }

    Given("큐에 저장된 매수 주문이 있을 때") {
        val request = ExchangeOrderRequest(
            userId = 1L,
            type = OrderType.BUY,
            currencyCode = "USD",
            amount = BigDecimal("100.00")
        )

        When("dequeue 메서드를 호출하면") {
            every { redisOrderQueue.dequeue() } returns request

            val result = redisOrderQueue.dequeue()

            Then("해당 주문이 반환되어야 한다") {
                result shouldBe request
            }
        }
    }
})
