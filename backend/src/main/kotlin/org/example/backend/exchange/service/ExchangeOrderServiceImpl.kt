package org.example.backend.exchange.service

import org.example.backend.common.util.toDTO
import org.example.backend.enums.CompanyAccount
import org.example.backend.enums.ExchangeLedgerType
import org.example.backend.enums.OrderStatus
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.dto.ExchangeLedgerCommand
import org.example.backend.exchange.dto.ExchangeOrderResponse
import org.example.backend.exchange.dto.TransactionCommand
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.service.WalletService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ExchangeOrderServiceImpl(
    private val exchangeOrderRepository: ExchangeOrderRepository,
    private val exchangeService: ExchangeService,
    private val transactionService: TransactionService,
    private val ledgerService: ExchangeLedgerService,
    private val currencyRepository: CurrencyRepository,
    private val walletService: WalletService,
    private val accountRepository: AccountRepository
): ExchangeOrderService {
    private val log = LoggerFactory.getLogger(ExchangeOrderServiceImpl::class.java)

    val commission = BigDecimal("0.0005") // 0.05% 수수료

    @Transactional
    override fun buyOrder(userId: Long, currencyCode: String, fromAmount: BigDecimal, isArbitrage: Boolean): ExchangeOrderResponse {
        var stage = "INIT"

        // 통화 조회
        val fromCurrency = currencyRepository.findByCode("KRW")
        val toCurrency = currencyRepository.findByCode(currencyCode)

        // 환전 계산
        val bestRate = if (isArbitrage) exchangeService.getBestBuyBaseRate(currencyCode) else exchangeService.getBestBuyRate(currencyCode)

        require(fromAmount >= bestRate.bestRate) {
            // 최소 환전 금액 미달 시 예외 처리
            "Minimum exchange amount not met for $currencyCode"
        }

        val order = ExchangeOrder(
                userId = userId,
                bankId = bestRate.bankId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = fromAmount,
                toAmount = BigDecimal.ZERO, // 임시값
                exchangeRate = bestRate.bestRate,
                status = OrderStatus.PENDING,
        )
        val savedOrder = exchangeOrderRepository.save(order) // 영속 상태
        val orderId: Long = savedOrder.id!!

        return try {
            stage = "CALC_EXCHANGE"
            val commissionAmount = if(isArbitrage) BigDecimal.ZERO else fromAmount.times(commission).setScale(0, RoundingMode.UP) // 환전 수수료 (정수부분까지 올림)
            val exchangeAmount = fromAmount.minus(commissionAmount) // 실제 환전할 금액 = 환전된 금액 - 수수료

            val (toAmount, bankProfit) = exchangeService.calculateExchange(
                fromCurrency = fromCurrency,
                toCurrency = toCurrency,
                exchangeRate = bestRate.bestRate,
                fromAmount = exchangeAmount,
            )

            savedOrder.toAmount = toAmount // 최종 환전된 금액
            log.info("buyOrder[{}] calc -> (원화 {} - 수수료{}) -> 환전 전 원화 {} -> 환전 후 외화 {}, 은행 차익 {}",
                orderId, fromAmount, commissionAmount, exchangeAmount, toAmount, bankProfit)

            // 3. 가상 금액 이동
            // 회사 계좌 조회
            val commissionMeta = CompanyAccount.COMMISSION_ACCOUNT
            val krwMeta       = CompanyAccount.ofCurrencyId(fromCurrency.id)
            val fxMeta        = CompanyAccount.ofCurrencyId(toCurrency.id)

            val companyCommissionAccount = accountRepository.findByCurrencyIdAndAccountNum(1, commissionMeta.accountNum)
                ?: error("Company account not found")
            val companyKRWAccount = accountRepository.findByCurrencyIdAndAccountNum(fromCurrency.id, krwMeta.accountNum)
                ?: error("Company KRW account not found")
            val companyFXAccount = accountRepository.findByCurrencyIdAndAccountNum(toCurrency.id, fxMeta.accountNum)
                ?: error("Company FX account not found")

            // 유저 원화 지갑 fromAmount -> 회사 원화 계좌 exchangeAmount, 회사 수수료 계좌 commissionAmount
            stage = "FUND_USER_TO_COMPANY_KRW"
            walletService.userToCompany(userId, fromCurrency.id, companyKRWAccount.id!!, exchangeAmount)
            log.info("order[{}] step={}", orderId, stage)

            stage = "FUND_USER_TO_COMPANY_COMMISSION"
            walletService.userToCompany(userId, fromCurrency.id, companyCommissionAccount.id!!, commissionAmount)
            log.info("order[{}] step={}", orderId, stage)

            // 회사 원화 계좌 exchangeAmount -> 은행
            stage = "FUND_COMPANY_KRW_TO_BANK"
            walletService.bankToCompany(companyKRWAccount.id!!, fromCurrency.id, exchangeAmount.negate())
            log.info("order[{}] step={}", orderId, stage)

            // 은행 -> 회사 외화 계좌 toAmount, 은행 차익 bankProfit
            stage = "FUND_BANK_TO_COMPANY_FX"
            walletService.bankToCompany(companyFXAccount.id!!, toCurrency.id, toAmount)
            log.info("order[{}] step={}", orderId, stage)

            // 회사 외화 계좌 -> 유저 외화 지갑 toAmount
            stage = "FUND_COMPANY_TO_USER_FX"
            walletService.companyToUser(companyFXAccount.id!!, userId, toCurrency.id, toAmount)
            log.info("order[{}] step={}", orderId, stage)

            // 4. 더티 체킹으로 주문 SUCCESS 업데이트
            stage = "UPDATE_ORDER_STATUS"
            savedOrder.status = OrderStatus.SUCCESS

            // 5. 거래 기록 저장
            stage = "TRANSACTION_RECORD"

            val userToCompany = TransactionCommand(
                userId = userId,
//                walletId = userWallet.id, // 사용자 지갑 ID
                orderId = orderId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = fromAmount,
                toAmount = toAmount,
                exchangeRate = bestRate.bestRate,
                commissionCurrencyId = fromCurrency.id,
                commissionAmount = commissionAmount, // commission : 유저가 앱에서 환전을 할 때 발생하는 수수료 (회사가 받는 이익)
                profitCurrencyId = null,
                profit = null
            )
            transactionService.record(userToCompany)

            val companyToBank = TransactionCommand(
                userId = 1L, // 회사 ID
//                walletId = companyWallet.id, // 회사 지갑 ID
                orderId = orderId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = exchangeAmount,
                toAmount = toAmount,
                exchangeRate = bestRate.bestRate,

                // 회사-은행간의 수수료 미정
                commissionCurrencyId = null,
                commissionAmount = null,

                profitCurrencyId = toCurrency.id,
                profit = bankProfit // profit : 환전으로 인해 소수점 이하가 발생하는 경우, 그 차액 (은행이 받는 이익)
            )
            transactionService.record(companyToBank)

            // 6. 환전 장부 기록 저장
            stage = "LEDGER_RECORD"

            val ledgerDto = ExchangeLedgerCommand(
                userId = userId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = fromAmount,
                toAmount = toAmount,
                exchangeRate = bestRate.bestRate,
                commissionCurrencyId = fromCurrency.id,
                commissionRate = commission,
                commissionAmount = commissionAmount,
                type = ExchangeLedgerType.FX_BUY,
            )

            ledgerService.record(ledgerDto)

            return savedOrder.toDTO()
        } catch (e: Exception) {
            // 주문 실패 시, 주문 상태를 FAILED로 변경
            log.error("buyOrder[{}] FAILED at stage={} : {}", orderId, stage, e.message, e)

            savedOrder.status = OrderStatus.FAILED

            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()

            savedOrder.toDTO()
        }
    }

    @Transactional
    override fun sellOrder(userId: Long, currencyCode: String, fromAmount: BigDecimal, isArbitrage: Boolean): ExchangeOrderResponse {
        var stage = "INIT"

        // 통화 조회
        val fromCurrency = currencyRepository.findByCode(currencyCode)
        val toCurrency = currencyRepository.findByCode("KRW")

        val bestRate = if (isArbitrage) exchangeService.getBestSellBaseRate(currencyCode) else exchangeService.getBestSellRate(currencyCode)

        require(fromAmount >= toCurrency.unit) {
            // 최소 환전 금액 미달 시 예외 처리
            "Minimum exchange amount not met for $currencyCode"
        }

        val order = ExchangeOrder(
            userId = userId,
            bankId = bestRate.bankId,
            fromCurrencyId = fromCurrency.id,
            toCurrencyId = toCurrency.id,
            fromAmount = fromAmount,
            toAmount = BigDecimal.ZERO, // 임시값
            exchangeRate = bestRate.bestRate,
            status = OrderStatus.PENDING,
        )
        val savedOrder = exchangeOrderRepository.save(order)
        val orderId: Long = savedOrder.id ?: error("Order ID is null")

        return try {
            stage = "EXCHANGE"
            val (rawToAmount, bankProfit) = exchangeService.calculateExchange(
                fromCurrency = fromCurrency,
                toCurrency = toCurrency,
                exchangeRate = bestRate.bestRate,
                fromAmount = fromAmount,
            )

            // 수수료 계산
            val commissionAmount = rawToAmount.times(commission).setScale(0, RoundingMode.UP)  // 환전 수수료 (정수부분까지 올림)
            val toAmount = rawToAmount.minus(commissionAmount) // 실제 환전된 금액 = 환전된 금액 - 수수료

            savedOrder.toAmount = toAmount // 최종 환전된 금액

            log.info("sellOrder[{}] calc -> 외화 {} -> 환전 후 원화 {} (최종 원화 {} + 수수료 {}) + 은행 차익 {}",
                orderId, fromAmount, rawToAmount, toAmount, commissionAmount, bankProfit)

            // 3. 가상 금액 이동
            // 회사 계좌 조회
            val commissionMeta = CompanyAccount.COMMISSION_ACCOUNT
            val krwMeta       = CompanyAccount.ofCurrencyId(toCurrency.id) // KRW
            val fxMeta        = CompanyAccount.ofCurrencyId(fromCurrency.id)   // USD/JPY 등

            val companyCommissionAccount = accountRepository.findByCurrencyIdAndAccountNum(1, commissionMeta.accountNum)
                ?: error("Company account not found")
            val companyKRWAccount = accountRepository.findByCurrencyIdAndAccountNum(toCurrency.id, krwMeta.accountNum)
                ?: error("Company KRW account not found")
            val companyFXAccount = accountRepository.findByCurrencyIdAndAccountNum(fromCurrency.id, fxMeta.accountNum)
                ?: error("Company FX account not found")

            // 유저 외화 지갑 -> 회사 외화 계좌 fromAmount
            stage = "FUND_USER_TO_COMPANY_KRW"
            walletService.userToCompany(userId, fromCurrency.id, companyFXAccount.id!!, fromAmount)
            log.info("order[{}] step={}", orderId, stage)

            // 회사 외화 계좌 -> 은행 fromAmount
            stage = "FUND_COMPANY_FX_TO_BANK"
            walletService.bankToCompany(companyFXAccount.id!!, bestRate.bankId, fromAmount.negate())
            log.info("order[{}] step={}", orderId, stage)

            // 은행 -> 회사 원화 계좌 rawToAmount, 은행 차익 bankProfit
            stage = "FUND_BANK_TO_COMPANY_KRW"
            walletService.bankToCompany(companyKRWAccount.id!!, toCurrency.id, rawToAmount)
            log.info("order[{}] step={}", orderId, stage)

            // 회사 원화 계좌 -> 회사 수수료 계좌 commissionAmount, 유저 원화 지갑 toAmount
            stage = "FUND_USER_TO_COMPANY_COMMISSION"
            walletService.companyToCompany(companyKRWAccount.id!!, companyCommissionAccount.id!!, commissionAmount)
            log.info("order[{}] step={}", orderId, stage)

            stage = "FUND_COMPANY_TO_USER_KRW"
            walletService.companyToUser(companyKRWAccount.id!!, userId, toCurrency.id, toAmount)
            log.info("order[{}] step={}", orderId, stage)

            // 4. 더티 체킹으로 주문 SUCCESS 업데이트
            stage = "UPDATE_ORDER_STATUS"
            savedOrder.status = OrderStatus.SUCCESS

            // 5. 거래 기록 저장
            stage = "TRANSACTION_RECORD"

            val userToCompany = TransactionCommand(
                userId = userId,
//                walletId = userWallet.id, // 사용자 지갑 ID
                orderId = orderId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = fromAmount,
                toAmount = toAmount,
                exchangeRate = bestRate.bestRate,
                commissionCurrencyId = toCurrency.id,
                commissionAmount = commissionAmount, // commission : 유저가 앱에서 환전을 할 때 발생하는 수수료 (회사가 받는 이익)
                profitCurrencyId = null,
                profit = null
            )
            transactionService.record(userToCompany)

            val companyToBank = TransactionCommand(
                userId = 1L, // 회사 ID
//                walletId = companyWallet.id, // 회사 지갑 ID
                orderId = orderId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = fromAmount,
                toAmount = rawToAmount,
                exchangeRate = bestRate.bestRate,

                // 회사-은행간의 수수료 미정
                commissionCurrencyId = null,
                commissionAmount = null,

                profitCurrencyId = toCurrency.id,
                profit = bankProfit // profit : 환전으로 인해 소수점 이하가 발생하는 경우, 그 차액 (은행이 받는 이익)
            )
            transactionService.record(companyToBank)

            // 6. 환전 장부 기록 저장
            stage = "LEDGER_RECORD"

            val ledgerDto = ExchangeLedgerCommand(
                userId = userId,
                fromCurrencyId = fromCurrency.id,
                toCurrencyId = toCurrency.id,
                fromAmount = fromAmount,
                toAmount = toAmount,
                exchangeRate = bestRate.bestRate,
                commissionCurrencyId = toCurrency.id,
                commissionRate = commission,
                commissionAmount = commissionAmount,
                type = ExchangeLedgerType.FX_SELL,
            )
            ledgerService.record(ledgerDto)

            return savedOrder.toDTO()
        } catch (e: Exception) {
            log.error("sellOrder[{}] FAILED at stage={} : {}", orderId, stage, e.message, e)

            // 주문 실패 시, 주문 상태를 FAILED로 변경
            savedOrder.status = OrderStatus.FAILED

            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()

            savedOrder.toDTO()
        }
    }

    @Transactional
    override fun arbitrageOrder(userId: Long, currencyCode: String, fromAmount: BigDecimal, isArbitrage: Boolean): Pair<ExchangeOrderResponse, ExchangeOrderResponse> {
        // 1. BuyOrder 실행: KRW → 외화
        val buy = buyOrder(userId, currencyCode, fromAmount, true)

        log.error("arbitrageOrder: buy={}", buy)

        if (buy.status != OrderStatus.SUCCESS) {
            // 매수 실패/취소면 매도는 수행하지 않고 바로 리턴
            log.error("arbitrageOrder: buy order failed or cancelled")

            return Pair(buy, buy)
        }
        // 2. SellOrder 실행: 외화 → KRW (fromAmount : 방금 매수한 외화)
        val sell = sellOrder(userId, currencyCode, buy.toAmount, true)

        log.error("arbitrageOrder: sell={}", sell)

        return Pair(buy, sell)
    }
}
