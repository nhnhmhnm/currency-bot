package org.example.backend.common.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date

// 토큰 생성 및 토큰 정보 추출
@Component
class JwtTokenProvider (
  private val log: Logger = LoggerFactory.getLogger(JwtTokenProvider::class.java),

  @Value("\${jwt.secret}")
  private val secretKey: String,

  @Value("\${jwt.access_expiration}")
  private val accessExpiration: Long,

  @Value("\${jwt.refresh_expiration}")
  private val refreshExpiration: Long
) {
  // secretKey -> base64 decode -> HMAC-SHA256에 사용할 키 객체로 변환
  private val key by lazy { Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey)) }

  private fun createToken(userId: Long, expiration: Long): String {
    val now = Date()
    val expiry = Date(now.time + expiration)
    return Jwts.builder()
      .setSubject(userId.toString())
      .setIssuedAt(now)
      .setExpiration(expiry)
      .signWith(key, SignatureAlgorithm.HS256)
      .compact()
  }

  // Access Token 생성
  fun createAccessToken(userId: Long) = createToken(userId, accessExpiration)
  // Refresh Token 생성
  fun createRefreshToken(userId: Long) = createToken(userId, refreshExpiration)


  // 유효성 검사 (서명 확인 + 만료 여부)
  fun validateToken(token: String): Boolean {
    return try {
      val claims = Jwts.parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)

      log.info("✅ Token valid. ExpiresAt=${claims.body.expiration}")
      true
    } catch (e: Exception) {
      log.warn("❌ JWT validation failed: ${e.message}")
      false
    }
  }

  // 토큰에서 userId 추출
  fun getUserId(token: String): Long {
    val claims = Jwts.parserBuilder()
      .setSigningKey(key)
      .build()
      .parseClaimsJws(token)
      .body

    return claims.subject.toLong()
    }

  fun getAccessExpiration(): Long = accessExpiration
  fun getRefreshExpiration(): Long = refreshExpiration
}