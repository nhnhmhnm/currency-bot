package org.example.backend.common.config

import org.example.backend.common.jwt.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
  private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {
  @Bean
  fun filterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .csrf { it.disable() } // jwt 사용 시 CSRF 불 필요
      .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
      .authorizeHttpRequests {
        it
          .requestMatchers(HttpMethod.POST,"/api/user/signup").permitAll()
          .requestMatchers(HttpMethod.POST, "/api/user/login").permitAll()
          .anyRequest().authenticated() // 나머지 요청은 인증 필요
      }
      .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java) // 커스텀 필터 등록
    return http.build()
  }

  @Bean
  fun passwordEncoder(): PasswordEncoder {
    return BCryptPasswordEncoder()
  }
}