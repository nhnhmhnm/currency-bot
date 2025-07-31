package org.example.backend.auth.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RedisTokenServiceImpl (
  private val redisTemplate: StringRedisTemplate
) : RedisTokenService{
  // Access Token 저장
  override fun saveAccessToken(userId: Long, token: String, expireTimeMs: Long) {
    val key = RedisKey.accessToken(userId)
    redisTemplate.opsForValue().set(key, token, Duration.ofMillis(expireTimeMs))
  }

  // Refresh Token 저장
  override fun saveRefreshToken(userId: Long, token: String, expireTimeMs: Long) {
    val key = RedisKey.refreshToken(userId)
    redisTemplate.opsForValue().set(key, token, Duration.ofMillis(expireTimeMs))
  }

  // Access Token 조회
  override fun getAccessToken(userId: Long): String? {
    val accessKey = RedisKey.accessToken(userId)
    return redisTemplate.opsForValue().get(accessKey)
  }

  // Refresh Token 조회
  override fun getRefreshToken(userId: Long): String? {
    val refreshKey = RedisKey.refreshToken(userId)
    return redisTemplate.opsForValue().get(refreshKey)
  }

  // Token 삭제
  override fun deleteAccessToken(userId: Long) {
    redisTemplate.delete(RedisKey.accessToken(userId))
  }

  override fun deleteRefreshToken(userId: Long) {
    redisTemplate.delete(RedisKey.refreshToken(userId))
  }

  override fun isSameToken(expected: String, actual: String?): Boolean {
    return !actual.isNullOrBlank() && expected == actual
  }
}