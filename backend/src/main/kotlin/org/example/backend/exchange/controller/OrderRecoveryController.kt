package org.example.backend.exchange.controller

import org.example.backend.auth.dto.UserPrincipal
import org.example.backend.exchange.service.OrderRecoveryResult
import org.example.backend.exchange.service.OrderRecoveryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class RequeueOrderRequest(val reason: String)

@RestController
@RequestMapping("/api/admin/orders")
class OrderRecoveryController(private val recovery: OrderRecoveryService) {
    @PostMapping("/{orderId}/requeue")
    fun requeue(
        @AuthenticationPrincipal actor: UserPrincipal,
        @PathVariable orderId: Long,
        @RequestBody request: RequeueOrderRequest
    ): ResponseEntity<OrderRecoveryResult> {
        if (request.reason.trim().length !in 3..500) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Recovery reason must contain 3 to 500 characters")
        }
        val result = recovery.requeue(orderId, actor.id, request.reason)
        val status = if (result.result == "QUEUED") HttpStatus.ACCEPTED else HttpStatus.CONFLICT
        return ResponseEntity.status(status).body(result)
    }
}
