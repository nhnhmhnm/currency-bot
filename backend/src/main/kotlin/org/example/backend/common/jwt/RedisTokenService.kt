package org.example.backend.common.jwt

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RedisTokenService (
  private val redisTemplate: StringRedisTemplate
) {
  // access token을 redis에 저장
  fun saveToken(userId: Long, token: String, expireTimeMs: Long) {
    val key = "access_token:$userId"
    redisTemplate.opsForValue().set(key, token, Duration.ofMillis(expireTimeMs))
  }

  // access 토큰 조회
  fun getToken(userId: Long): String? {
    val key = "access_token:$userId"
    return redisTemplate.opsForValue().get(key)
  }

  fun deleteToken(userId: Long) {
    redisTemplate.delete("access_token:$userId")
  }
}