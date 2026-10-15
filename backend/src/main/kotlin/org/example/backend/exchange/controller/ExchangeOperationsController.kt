package org.example.backend.exchange.controller

import org.example.backend.exchange.service.ExchangeOperationsSnapshot
import org.example.backend.exchange.service.ExchangeOperationsSnapshotService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/exchange")
class ExchangeOperationsController(private val operations: ExchangeOperationsSnapshotService) {
    @GetMapping("/operations")
    fun snapshot(): ExchangeOperationsSnapshot = operations.snapshot()
}
