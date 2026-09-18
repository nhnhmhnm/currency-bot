package org.example.backend.common.util

data class OrderQueueMessage(
    val token: String,
    val orderId: Long
)
