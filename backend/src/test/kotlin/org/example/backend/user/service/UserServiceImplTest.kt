package org.example.backend.user.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
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

class UserServiceImplTest : FunSpec({
    val userRepository = mockk<UserRepository>()
    val passwordEncoder = mockk<PasswordEncoder>()
    val jwtTokenProvider = mockk<JwtTokenProvider>()
    val redisTokenService = mockk<RedisTokenService>(relaxed = true)
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

    test("createUser saves a user and creates KRW/USD/JPY wallets") {
        val req = UserSignupRequest(
            device = "test-device",
            name = "tester",
            password = "1234",
            phone = "010-1234-5678",
            type = UserType.GENERAL
        )
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
        val krw = Currency(id = 1L, code = "KRW", name = "Korean Won", scale = 0, unit = BigDecimal.ONE)
        val usd = Currency(id = 2L, code = "USD", name = "US Dollar", scale = 2, unit = BigDecimal.ONE)
        val jpy = Currency(id = 3L, code = "JPY", name = "Japanese Yen", scale = 0, unit = BigDecimal("100"))
        val walletSlot = slot<Iterable<Wallet>>()

        every { userRepository.existsByDevice(req.device) } returns false
        every { passwordEncoder.encode(req.password) } returns "ENC(1234)"
        every { userRepository.save(any<User>()) } returns savedUser
        every { currencyRepository.findByCode("KRW") } returns krw
        every { currencyRepository.findByCode("USD") } returns usd
        every { currencyRepository.findByCode("JPY") } returns jpy
        every { walletRepository.saveAll(capture(walletSlot)) } answers { walletSlot.captured.toList() }

        val res = service.createUser(req)

        res.id shouldBe 1L
        res.name shouldBe "tester"

        val created = walletSlot.captured.toList()
        created.shouldHaveSize(3)
        created.map { it.userId }.distinct() shouldBe listOf(1L)
        created.map { it.currencyId }.sorted() shouldBe listOf(1L, 2L, 3L)
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

    test("createUser rejects a duplicated device") {
        val req = UserSignupRequest(
            device = "dup-device",
            name = "tester",
            password = "1234",
            phone = "010-1234-5678",
            type = UserType.GENERAL
        )

        every { userRepository.existsByDevice("dup-device") } returns true

        val exception = shouldThrow<UserException> { service.createUser(req) }
        exception.status shouldBe ErrorCode.DUPLICATED_USER_DEVICE.httpStatus
        exception.errorCode shouldBe ErrorCode.DUPLICATED_USER_DEVICE.code

        verify(exactly = 1) { userRepository.existsByDevice("dup-device") }
        confirmVerified(userRepository, walletRepository, currencyRepository)
    }

    test("login creates tokens and stores them in Redis") {
        val user = User(
            id = 2L,
            device = "device2",
            name = "tester",
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
            redisTokenService.saveRefreshToken(2L, "refresh_token:2", 60L * 60 * 24 * 7)
        }
    }
})
