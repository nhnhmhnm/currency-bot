package org.example.backend.finance.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["app.accounting.reconciliation-enabled"], havingValue = "true")
class JournalReconciliationJob(private val reconciliation: JournalReconciliationService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.accounting.reconciliation-delay-ms:86400000}")
    fun run() {
        val report = reconciliation.audit()
        if (report.balanced) log.info("journal reconciliation passed")
        else log.error("journal reconciliation found {} issues: {}", report.issues.size, report.issues.take(20))
    }
}
