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
                // 비인증 허용 경로
                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/user/signup",
                    "/api/auth/login",
                    "/api/auth/reissue",
                    "/api/account"
                ).permitAll()

                // 인증 필요 경로
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/user/me",
                    "/api/exchange/buy",
                    "/api/exchange/sell",
                    "/api/exchange/arbitrage"
                ).authenticated()

                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/logout",
                    "/api/wallet/**"
                ).authenticated()

                // 나머지 요청은 인증 필요
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java) // 커스텀 필터 등록
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}