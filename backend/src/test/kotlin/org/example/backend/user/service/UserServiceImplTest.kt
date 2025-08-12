package org.example.backend.user.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifySequence
import org.example.backend.auth.dto.TokenResponse
import org.example.backend.auth.dto.UserLoginRequest
import org.example.backend.auth.service.RedisTokenService
import org.example.backend.common.jwt.JwtTokenProvider
import org.example.backend.enums.UserType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.user.domain.User
import org.example.backend.user.domain.Wallet
import org.example.backend.user.dto.UserSignupRequest
import org.example.backend.user.repository.UserRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.security.crypto.password.PasswordEncoder
import java.math.BigDecimal
import java.time.LocalDateTime

class UserServiceImplTest : BehaviorSpec ({

  // Mocks
  val userRepository = mockk<UserRepository>()
  val passwordEncoder = mockk<PasswordEncoder>()
  val jwtTokenProvider = mockk<JwtTokenProvider>()
  val redisTokenService = mockk<RedisTokenService>()
  val walletRepository = mockk<WalletRepository>()
  val currencyRepository = mockk<CurrencyRepository>()

  val service = UserServiceImpl(
    userRepository = userRepository,
    passwordEncoder = passwordEncoder,
    jwtTokenProvider = jwtTokenProvider,
    redisTokenService = redisTokenService,
    walletRepository = walletRepository,
    currencyRepository = currencyRepository
  )

  beforeTest {
    clearAllMocks()
  }

  Given("createUser") {
    When("등록되지 않은 디바이스로 회원 가입하면") {
      Then("유저가 저장되고, 계좌와 연결되지 않은 KRW/USD/JPY 지갑이 생성된다.") {
        // given
        val req = UserSignupRequest(
          device = "test-device",
          name = "이재웅",
          password = "1234",
          phone = "010-1234-5678",
          type = UserType.GENERAL
        )

        every { userRepository.existsByDevice(req.device) } returns false
        every { passwordEncoder.encode(req.password) } returns "ENC(1234)"

        // save 호출 시 id 부여된 User 반환
        val savedUser = User(
          id = 1L,
          device = req.device,
          name = req.name,
          password = "ENC(1234)",
          phone = req.phone,
          type = req.type,
          isActive = true,
          createdAt = LocalDateTime.now()
        )
        every { userRepository.save(any<User>()) } returns savedUser

        // 통화 3종 반한
        val krw = Currency(id = 1L, code = "krw", name = "대한민국 원", scale = 0, unit = BigDecimal("1"))
        val usd = Currency(id = 2L, code = "usd", name = "미국 달러", scale = 2, unit = BigDecimal("1"))
        val jpy = Currency(id = 3L, code = "jpy", name = "일본 엔", scale = 0, unit = BigDecimal("100"))
        every { currencyRepository.findByCode("KRW") } returns krw
        every { currencyRepository.findByCode("USD") } returns usd
        every { currencyRepository.findByCode("JPY") } returns jpy

        val walletSlot = slot<Iterable<Wallet>>()
        every { walletRepository.saveAll(capture(walletSlot)) } answers { walletSlot.captured.toList() }

        // when
        val res = service.createUser(req)

        // then
        res.id shouldBe 1L
        res.name shouldBe "이재웅"

        walletSlot.captured.toList().shouldHaveSize(3)
        val created = walletSlot.captured.toList()
        created.map { it.userId }.distinct() shouldBe listOf(1L)
        created.map { it.currencyId }.sorted() shouldBe listOf(10L, 20L, 30L)
        created.forEach { it.isConnected shouldBe false }

        verifySequence {
          userRepository.existsByDevice("test-device")
          passwordEncoder.encode("1234")
          userRepository.save(any<User>())
          currencyRepository.findByCode("KRW")
          currencyRepository.findByCode("USD")
          currencyRepository.findByCode("JPY")
          walletRepository.saveAll(any<Iterable<Wallet>>())
        }
      }
    }

    When("이미 등록된 디바이스로 회원 가입하면") {
      Then("UserException(DUPLICATE_DEVICE)이 발생한다.") {
        // given
        val req = UserSignupRequest(
          device = "dup-device",
          name = "이재웅",
          password = "1234",
          phone = "010-1234-5678",
          type = UserType.GENERAL
        )
        every { userRepository.existsByDevice("dup-device") } returns true

        // when, then
        val exception = shouldThrow<UserException> { service.createUser(req) }
        exception.status shouldBe ErrorCode.DUPLICATED_USER_DEVICE.httpStatus
        exception.errorCode shouldBe 101

        verify(exactly = 1) { userRepository.existsByDevice("dup-device") }
        confirmVerified(userRepository, walletRepository, currencyRepository)
      }
    }
  }

  Given("login") {
    When("등록된 유저가 올바른 비밀번호로 로그인하면") {
      Then("Access/Refresh 토큰 생성 후 Redis에 저장한다") {
        val user = User(
          id = 2L,
          device = "device2",
          name = "이재웅",
          password = "ENC(1234)",
          phone = "010-1234-5678",
          type = UserType.GENERAL,
          isActive = true,
          createdAt = LocalDateTime.now()
        )
        every { userRepository.findByDevice("device2") } returns user
        every { passwordEncoder.matches("1234", "ENC(1234)") } returns true

        every { jwtTokenProvider.createAccessToken(2L) } returns "access_token:2"
        every { jwtTokenProvider.createRefreshToken(2L) } returns "refresh_token:2"
        every { jwtTokenProvider.getAccessExpiration() } returns 1800L
        every { jwtTokenProvider.getRefreshExpiration() } returns 60L * 60 * 24 * 7

        val res: TokenResponse = service.login(UserLoginRequest(device = "device2", password = "1234"))

        res.accessToken shouldBe "access_token:2"
        res.refreshToken shouldBe "refresh_token:2"

        verify {
          redisTokenService.saveAccessToken(2L, "access_token:2", 1800L)
          redisTokenService.saveRefreshToken(2L, "refresh_token:2", any())
        }
      }
    }


  }
}
)