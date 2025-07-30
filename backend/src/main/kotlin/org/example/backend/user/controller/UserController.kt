package org.example.backend.user.controller

import org.example.backend.user.domain.User
import org.example.backend.auth.dto.UserLoginRequest
import org.example.backend.user.dto.UserMeResponse
import org.example.backend.user.dto.UserSignupRequest
import org.example.backend.user.dto.UserSignupResponse
import org.example.backend.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/user")
class UserController (
  private val userService: UserService
) {
  @PostMapping("/signup")
  fun signup(@RequestBody request: UserSignupRequest): ResponseEntity<UserSignupResponse> {
    val response = userService.createUser(request)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
  }

  @GetMapping("/me")
  fun getMyInfo(): ResponseEntity<UserMeResponse> {
    // SecurityContext 에서 userId 꺼냄
    val authentication = SecurityContextHolder.getContext().authentication
    val user = authentication.principal as User
    val userId = user.id ?: throw RuntimeException("UserId not found")

    val userInfo = userService.getMyInfo(userId)
    return ResponseEntity.ok(userInfo)
  }
}