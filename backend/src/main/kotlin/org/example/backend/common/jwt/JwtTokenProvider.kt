package org.example.backend.common.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date

// 토큰 생성 및 토큰 정보 추출
@Component
class JwtTokenProvider (
  @Value("\${jwt.secret}")
  private val secretKey: String,

  @Value("\${jwt.expiration}")
  private val expiration: Long
) {
  // secreyKey -> base64 decdoe -> HMAC-SHA256에 사용할 키 객체로 변환
  private val key by lazy { Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey)) }

  // 토큰 생성 (User id 기반)
  fun createToken(userId: Long): String {
    val now = Date()
    val expiry = Date(now.time + expiration)

    return Jwts.builder()
      .setSubject(userId.toString()) // 사용자 고유 식별값
      .setIssuedAt(now) // 토큰 발급 시간
      .setExpiration(expiry) // 만료 시간
      .signWith(key, SignatureAlgorithm.HS256) // 서명 알고리즘 + 키
      .compact() // 토큰 문자열 생성
  }

  // 유효성 검사 (서명 확인 + 만료 여부)
  fun validateToken(token: String): Boolean {
    return try {
      Jwts.parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
      true
    } catch (e: Exception) {
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
}