package org.example.backend.exchange.service

import org.example.backend.common.util.OrderQueueMessage
import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.enums.OrderStatus
import org.example.backend.exchange.dto.ExchangeOrderResponse
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

@Service
class OrderWorker(
    private val queue: RedisOrderQueue,
    private val exchangeOrderService: ExchangeOrderService,
    private val exchangeOrderRepository: ExchangeOrderRepository
) {
    private val log = LoggerFactory.getLogger(OrderWorker::class.java)

    @Scheduled(fixedDelay = 500)
    fun processQueue() {
        queue.releaseDue()
        recoverExpiredMessages()

        var message = queue.claim()
        while (message != null) {
            processMessage(message)
            message = queue.claim()
        }
    }

    @Scheduled(fixedDelayString = "\${app.order.recovery-delay-ms:30000}")
    fun recoverIncompleteOrders() {
        var lastId = 0L
        val cutoff = LocalDateTime.now().minus(INCOMPLETE_AGE)
        do {
            val orders = exchangeOrderRepository
                .findTop100ByStatusInAndRequestedAtBeforeAndIdGreaterThanOrderByIdAsc(
                    RECOVERABLE_STATUSES,
                    cutoff,
                    lastId
                )
            orders.forEach { order -> queue.ensureQueued(order.id!!) }
            lastId = orders.lastOrNull()?.id ?: lastId
        } while (orders.size == 100)
    }

    private fun processMessage(message: OrderQueueMessage) {
        try {
            val result = exchangeOrderService.processQueuedOrder(message.orderId)
            logOrder(result)
            acknowledge(message)
        } catch (notDue: OrderRetryNotDueException) {
            defer(message, notDue.nextAttemptAt)
        } catch (failure: Exception) {
            log.error("order settlement failed: orderId={}", message.orderId, failure)
            recordFailureAndRelease(message, failure.message ?: failure::class.simpleName.orEmpty())
        }
    }

    private fun recoverExpiredMessages() {
        queue.recoverExpired(LEASE_TIMEOUT).forEach { message ->
            recordFailureAndRelease(message, "worker lease expired")
        }
    }

    private fun recordFailureAndRelease(message: OrderQueueMessage, reason: String) {
        val decision = try {
            exchangeOrderService.recordProcessingFailure(message.orderId, message.leaseId, reason, MAX_ATTEMPTS)
        } catch (dbFailure: Exception) {
            log.error("failed to persist retry state: orderId={}", message.orderId, dbFailure)
            return
        }

        if (decision.terminal) {
            acknowledge(message)
        } else {
            defer(message, requireNotNull(decision.nextAttemptAt))
        }
    }

    private fun acknowledge(message: OrderQueueMessage) {
        try {
            if (!queue.acknowledge(message)) {
                log.warn("queue ACK skipped after lease change: orderId={}", message.orderId)
            }
        } catch (queueFailure: Exception) {
            log.error("queue ACK failed after DB commit: orderId={}", message.orderId, queueFailure)
        }
    }

    private fun defer(message: OrderQueueMessage, dueAt: Instant) {
        try {
            if (!queue.defer(message, dueAt)) {
                log.warn("queue defer skipped after lease change: orderId={}", message.orderId)
            }
        } catch (queueFailure: Exception) {
            log.error("queue defer failed after DB commit: orderId={}", message.orderId, queueFailure)
        }
    }

    private fun logOrder(res: ExchangeOrderResponse) {
        log.info(
            "order processed: id={}, userId={}, status={}, fromCurrencyId={}, toCurrencyId={}, fromAmount={}, toAmount={}",
            res.id,
            res.userId,
            res.status,
            res.fromCurrencyId,
            res.toCurrencyId,
            res.fromAmount,
            res.toAmount
        )
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private val LEASE_TIMEOUT: Duration = Duration.ofSeconds(30)
        private val INCOMPLETE_AGE: Duration = Duration.ofSeconds(60)
        private val RECOVERABLE_STATUSES = listOf(
            OrderStatus.REQUESTED,
            OrderStatus.QUEUED,
            OrderStatus.PROCESSING,
            OrderStatus.PENDING
        )
    }
}
