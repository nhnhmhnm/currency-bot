package org.example.backend.user.repository

import org.example.backend.enums.UserType
import org.example.backend.user.domain.Account
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet

class AccountJdbcTemplateRepositoryImpl(
  private val jdbcTemplate: NamedParameterJdbcTemplate
) : AccountJdbcTemplateRepository {

  private val rowMapper = RowMapper<Account> { rs: ResultSet, _: Int ->
    Account(
      id = rs.getLong("id"),
      userId = rs.getLong("user_id"),
      bankId = rs.getLong("bank_id"),
      currencyId = rs.getLong("currency_id"),
      accountNum = rs.getString("account_num"),
      balance = rs.getBigDecimal("balance"),
      isActive = rs.getBoolean("is_active"),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime()
    )
  }
  override fun findByUserIdAndCurrencyId(userId: Long, currencyId: Long): Account? {
    val sql = """
      SELECT * FROM account
      WHERE user_id = :userId
      AND currency_id = :currencyId
      """.trimIndent()

    val params = mapOf("userId" to userId, "currencyId" to currencyId)

    return jdbcTemplate.query(sql, params, rowMapper).firstOrNull()
  }

  override fun findByBankIdAndAccountNum(bankId: Long, accountNum: String): Account? {
    val sql = """
      SELECT * FROM account
      WHERE bank_id = :bankId AND account_num = :accountNum
      """.trimIndent()

    val params = mapOf("bankId" to bankId, "accountNum" to accountNum)

    return jdbcTemplate.query(sql, params, rowMapper).firstOrNull()
  }

  override fun findSuperByCurrencyIdAndUserType(currencyId: Long, type: UserType): Account? {
    val sql = """
      SELECT a.* FROM account a
      JOIN user u ON a.user_id = u.id
      WHERE a.currency_id = :currencyId AND u.type = :userType
      """.trimIndent()

    val params = mapOf("currencyId" to currencyId, "userType" to type.name)

    return jdbcTemplate.query(sql, params, rowMapper).firstOrNull()
  }

}