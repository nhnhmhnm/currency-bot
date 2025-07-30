package org.example.backend.common.jwt

object JwtHeaderUtil {
  fun extractBearerToken(header: String?): String? {
    if (header.isNullOrBlank() || !header.startsWith("Bearer ")) return null
    return header.removePrefix("Bearer ").trim()
  }
}