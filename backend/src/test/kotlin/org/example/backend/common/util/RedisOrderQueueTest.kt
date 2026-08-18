package org.example.backend.common.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class RedisOrderQueueTest : BehaviorSpec({

    val redisOrderQueue = mockk<RedisOrderQueue>(relaxed = true)

    Given("an order id") {
        val orderId = 1L

        When("enqueue is called") {
            every { redisOrderQueue.enqueue(orderId) } returns Unit
            redisOrderQueue.enqueue(orderId)

            Then("the order id is enqueued") {
                verify { redisOrderQueue.enqueue(orderId) }
            }
        }
    }

    Given("an order id stored in queue") {
        val orderId = 1L

        When("dequeue is called") {
            every { redisOrderQueue.dequeue() } returns orderId

            val result = redisOrderQueue.dequeue()

            Then("the order id is returned") {
                result shouldBe orderId
            }
        }
    }
})
