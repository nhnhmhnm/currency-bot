package org.example.backend.auth.service

interface RedisTokenService {
  fun saveAccessToken(userId: Long, token: String, expireTimeMs: Long)
  fun saveRefreshToken(userId: Long, token: String, expireTimeMs: Long)
  fun getAccessToken(userId: Long): String?
  fun getRefreshToken(userId: Long): String?
  fun deleteAccessToken(userId: Long)
  fun deleteRefreshToken(userId: Long)
  fun isSameToken(expected: String, actual: String?): Boolean
}