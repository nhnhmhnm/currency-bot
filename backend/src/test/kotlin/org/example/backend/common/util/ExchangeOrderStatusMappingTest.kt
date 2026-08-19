package org.example.backend.common.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.enums.PublicOrderStatus
import org.example.backend.exchange.domain.ExchangeOrder
import java.math.BigDecimal

class ExchangeOrderStatusMappingTest : FunSpec({
    val mappings = mapOf(
        OrderStatus.REQUESTED to PublicOrderStatus.ACCEPTED,
        OrderStatus.QUEUED to PublicOrderStatus.ACCEPTED,
        OrderStatus.PENDING to PublicOrderStatus.ACCEPTED,
        OrderStatus.PROCESSING to PublicOrderStatus.PROCESSING,
        OrderStatus.COMPENSATING to PublicOrderStatus.PROCESSING,
        OrderStatus.SETTLED to PublicOrderStatus.COMPLETED,
        OrderStatus.SUCCESS to PublicOrderStatus.COMPLETED,
        OrderStatus.CANCELED to PublicOrderStatus.CANCELED,
        OrderStatus.COMPENSATED to PublicOrderStatus.CANCELED,
        OrderStatus.FAILED to PublicOrderStatus.FAILED
    )

    mappings.forEach { (internalStatus, publicStatus) ->
        test("maps internal $internalStatus to public $publicStatus") {
            val order = ExchangeOrder(
                id = 1L,
                userId = 10L,
                bankId = 0L,
                fromCurrencyId = 1L,
                toCurrencyId = 2L,
                fromAmount = BigDecimal("1000"),
                toAmount = BigDecimal.ZERO,
                exchangeRate = BigDecimal.ZERO,
                orderType = OrderType.BUY,
                status = internalStatus
            )

            order.toDto().status shouldBe publicStatus
        }
    }
})
