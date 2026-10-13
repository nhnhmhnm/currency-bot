package org.example.backend.exchange.service

import java.time.Instant

data class OrderRetryDecision(
    val terminal: Boolean,
    val nextAttemptAt: Instant? = null
)

class OrderRetryNotDueException(val nextAttemptAt: Instant) : RuntimeException("order retry is not due")
