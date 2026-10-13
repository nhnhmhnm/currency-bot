package org.example.backend.finance.repository

import jakarta.persistence.LockModeType
import org.example.backend.enums.CompanyAccount
import org.example.backend.finance.domain.InternalAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface InternalAccountRepository : JpaRepository<InternalAccount, Long> {
    fun findByCode(code: CompanyAccount): InternalAccount?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from InternalAccount a where a.id = :id")
    fun findByIdForUpdate(id: Long): InternalAccount?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from InternalAccount a where a.code = :code")
    fun findByCodeForUpdate(code: CompanyAccount): InternalAccount?
}
