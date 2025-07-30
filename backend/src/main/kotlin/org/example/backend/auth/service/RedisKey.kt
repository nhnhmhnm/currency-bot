package org.example.backend.auth.service

object RedisKey {
  fun accessToken(userId: Long) = "access_token:$userId"
  fun refreshToken(userId: Long) = "refresh_token:$userId"
}