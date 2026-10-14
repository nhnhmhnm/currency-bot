package org.example.backend.finance.repository

import org.example.backend.finance.domain.Journal
import org.example.backend.finance.domain.JournalLine
import org.example.backend.finance.domain.JournalSource
import org.springframework.data.jpa.repository.JpaRepository

interface JournalRepository : JpaRepository<Journal, Long> {
    fun existsBySourceTypeAndSourceId(sourceType: JournalSource, sourceId: Long): Boolean
}

interface JournalLineRepository : JpaRepository<JournalLine, Long> {
    fun findAllByOrderByIdAsc(): List<JournalLine>
}
