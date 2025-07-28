package org.example.backend.common.jwt

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.example.backend.user.repository.UserRepository
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
  private val jwtTokenProvider: JwtTokenProvider,
  private val redisTokenService: RedisTokenService,
  private val userRepository: UserRepository,
) : OncePerRequestFilter() { // 요청 당 한 번만 실행되는 필터
  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    // 1. 요청 헤더에서 Authorization 헤더 추출
    val authHeader = request.getHeader("Authorization")

    // 2. 헤더가 없거나 "Bearer"로 시작하지 않으면 다음 필터로 넘김
    if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response)
      return
    }

    // 3. 토큰만 추출 ("Bearer " 이후 문자열)
    val token = authHeader.substring(7)

    // 4. 토큰 유효성 검사
    if (!jwtTokenProvider.validateToken(token)) {
      filterChain.doFilter(request, response)
      return
    }

    // 5. JWT에서 userId 추출
    val userId = jwtTokenProvider.getUserId(token)

    // 6. Redis에 저장된 토큰과 비교
    val storedToken = redisTokenService.getToken(userId)
    if (storedToken.isNullOrBlank() || storedToken != token) {
      filterChain.doFilter(request, response)
      return
    }

    // 7. DB에서 user 정보 조회
    val user = userRepository.findById(userId)
      .orElse(null) ?: run {
        filterChain.doFilter(request, response)
      return
    }

    // 8. 인증 객체 생성 (without UserDetails)
    val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.type.name.uppercase()}"))
    val authToken = UsernamePasswordAuthenticationToken(
      user,
      null,
      authorities
    ).apply { details = WebAuthenticationDetailsSource().buildDetails(request) }

    // 9. 인증 객체를 SecurityContextHolder에 등록
    SecurityContextHolder.getContext().authentication = authToken

    // 10. 다음 필터로 넘기기
    filterChain.doFilter(request, response)
  }


}