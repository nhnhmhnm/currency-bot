package org.example.backend.user.service

import org.example.backend.user.dto.MoneyMovementCommand
import java.math.BigDecimal

interface MoneyMovementService {
    fun transfer(command: MoneyMovementCommand): BigDecimal?
}
