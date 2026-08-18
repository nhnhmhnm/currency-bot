package org.example.backend.enums

enum class OrderStatus {
    REQUESTED,
    QUEUED,
    PROCESSING,
    SETTLED,
    CANCELED,
    COMPENSATING,
    COMPENSATED,
    PENDING,
    SUCCESS,
    FAILED
}
