-- Generated from the current JPA model on an empty MySQL 9.4 rehearsal schema.
-- Import only into a new empty database. Never import into a populated cuunit schema.

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `account` (
  `is_active` bit(1) DEFAULT NULL,
  `bank_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `currency_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `account_num` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_bank_number` (`bank_id`,`account_num`),
  KEY `FK8pcnsdub6ul7jkr5s6dblgg1p` (`user_id`),
  CONSTRAINT `FK8pcnsdub6ul7jkr5s6dblgg1p` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bank` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKnc70mw7kj0k56c4pjpl6b0xwt` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `completed_order_view` (
  `from_amount` decimal(24,8) NOT NULL,
  `to_amount` decimal(24,8) NOT NULL,
  `completed_at` datetime(6) NOT NULL,
  `event_id` bigint NOT NULL,
  `from_currency_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `to_currency_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `order_type` enum('ARBITRAGE','BUY','SELL') NOT NULL,
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `UKcd6r8begdyo8g6e7sk7jpu5j0` (`event_id`),
  KEY `idx_completed_order_user_time` (`user_id`,`completed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `currency` (
  `scale` int NOT NULL,
  `unit` decimal(38,2) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKh84pd2rtr12isnifnj655rkra` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `deposit_withdrawal` (
  `amount` decimal(18,2) NOT NULL,
  `currency_id` bigint NOT NULL,
  `executed_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `wallet_id` bigint NOT NULL,
  `type` enum('DEPOSIT','WITHDRAWAL') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdvy7n9x1gp5xx8lt6aoy1w8tr` (`currency_id`),
  KEY `FKng7dhbcsd9jeqo272xmf2mxiu` (`user_id`),
  KEY `FKjgcxlrsfi6uiescuefrqsr5ku` (`wallet_id`),
  CONSTRAINT `FKdvy7n9x1gp5xx8lt6aoy1w8tr` FOREIGN KEY (`currency_id`) REFERENCES `currency` (`id`),
  CONSTRAINT `FKjgcxlrsfi6uiescuefrqsr5ku` FOREIGN KEY (`wallet_id`) REFERENCES `wallet` (`id`),
  CONSTRAINT `FKng7dhbcsd9jeqo272xmf2mxiu` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exchange_ledger` (
  `commission_amount` decimal(38,2) DEFAULT NULL,
  `commission_rate` decimal(18,10) DEFAULT NULL,
  `exchange_rate` decimal(38,2) NOT NULL,
  `from_amount` decimal(38,2) NOT NULL,
  `to_amount` decimal(38,2) NOT NULL,
  `commission_currency_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `from_currency_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `to_currency_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `type` enum('COMMISSION','DEPOSIT','FX_BUY','FX_SELL','OTHER','WITHDRAWAL') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exchange_order` (
  `arbitrage_slot_date` date DEFAULT NULL,
  `arbitrage_slot_number` int DEFAULT NULL,
  `exchange_rate` decimal(38,2) NOT NULL,
  `from_amount` decimal(38,2) NOT NULL,
  `quoted_counter_rate` decimal(38,2) DEFAULT NULL,
  `quoted_rate` decimal(38,2) DEFAULT NULL,
  `retry_attempts` int NOT NULL DEFAULT '0',
  `to_amount` decimal(38,2) NOT NULL,
  `bank_id` bigint NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `from_currency_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `next_attempt_at` datetime(6) DEFAULT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `requested_at` datetime(6) NOT NULL,
  `to_currency_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `last_failure_id` varchar(64) DEFAULT NULL,
  `idempotency_key` varchar(100) DEFAULT NULL,
  `failure_reason` varchar(255) DEFAULT NULL,
  `failure_stage` varchar(255) DEFAULT NULL,
  `order_type` enum('ARBITRAGE','BUY','SELL') NOT NULL,
  `status` enum('CANCELED','COMPENSATED','COMPENSATING','FAILED','PENDING','PROCESSING','QUEUED','REQUESTED','SETTLED','SUCCESS') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_order_user_idempotency_key` (`user_id`,`idempotency_key`),
  UNIQUE KEY `uk_exchange_order_arbitrage_daily_slot_v2` (`user_id`,`arbitrage_slot_date`,`arbitrage_slot_number`),
  KEY `idx_exchange_order_recovery` (`status`,`requested_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exchange_rate` (
  `base_rate` decimal(38,2) NOT NULL,
  `buy_rate` decimal(38,2) DEFAULT NULL,
  `sell_rate` decimal(38,2) DEFAULT NULL,
  `bank_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `currency_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_time` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_rate_bank_currency_notice` (`bank_id`,`currency_id`,`notice_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exchange_reservation` (
  `business_date` date NOT NULL,
  `from_amount` decimal(24,8) NOT NULL,
  `from_currency_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `status` enum('ACTIVE','CONSUMED','RELEASED') NOT NULL,
  PRIMARY KEY (`order_id`),
  KEY `idx_reservation_funds` (`user_id`,`from_currency_id`,`status`),
  KEY `idx_reservation_daily` (`user_id`,`business_date`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internal_account` (
  `balance` decimal(24,8) NOT NULL,
  `currency_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` enum('COMMISSION_ACCOUNT','JPY_ACCOUNT','KRW_ACCOUNT','USD_ACCOUNT') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsmypllpojy955lbucr9tyiby2` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `journal` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_id` bigint NOT NULL,
  `source_type` enum('EXCHANGE_ORDER','WALLET_TRANSFER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_journal_source` (`source_type`,`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `journal_line` (
  `balance_after` decimal(24,8) NOT NULL,
  `balance_before` decimal(24,8) NOT NULL,
  `credit` decimal(24,8) NOT NULL,
  `debit` decimal(24,8) NOT NULL,
  `line_number` int NOT NULL,
  `account_id` bigint NOT NULL,
  `currency_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `journal_id` bigint NOT NULL,
  `account_type` enum('COMMISSION_REVENUE','COMPANY_CASH','CUSTOMER_LIABILITY') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_journal_line_number` (`journal_id`,`line_number`),
  KEY `idx_journal_line_account` (`account_type`,`account_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_recovery_audit` (
  `actor_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `result` varchar(32) NOT NULL,
  `reason` varchar(500) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_recovery_order_time` (`order_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outbox_event` (
  `publish_attempts` int NOT NULL,
  `aggregate_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `published_at` datetime(6) DEFAULT NULL,
  `last_error` varchar(255) DEFAULT NULL,
  `event_type` enum('ORDER_ACCEPTED','ORDER_COMPLETED') NOT NULL,
  `status` enum('PENDING','PUBLISHED') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `simulated_bank_balance` (
  `balance` decimal(24,8) NOT NULL,
  `account_id` bigint NOT NULL,
  PRIMARY KEY (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transaction` (
  `commission_amount` decimal(38,2) DEFAULT NULL,
  `exchange_rate` decimal(38,2) NOT NULL,
  `from_amount` decimal(38,2) NOT NULL,
  `profit` decimal(18,10) DEFAULT NULL,
  `to_amount` decimal(38,2) NOT NULL,
  `commission_currency_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `from_currency_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `profit_currency_id` bigint DEFAULT NULL,
  `to_currency_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `phone` varchar(255) NOT NULL,
  `type` enum('GENERAL','SUPER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9739yj7oplvs0bveefw35hgph` (`device`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallet` (
  `balance` decimal(24,8) NOT NULL,
  `account_id` bigint DEFAULT NULL,
  `currency_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_user_currency` (`user_id`,`currency_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallet_fx_history` (
  `amount` decimal(38,2) NOT NULL,
  `balance_after` decimal(38,2) NOT NULL,
  `currency_id` bigint NOT NULL,
  `executed_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `wallet_id` bigint NOT NULL,
  `type` enum('COMMISSION','COMPANY_TO_USER','FX_BUY','FX_SELL','PROFIT','USER_TO_COMPANY') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1a2vlfsh855qqog0j6dp4h6aw` (`currency_id`),
  KEY `FKjddot67dfkn2kd7cg7cs39a3u` (`user_id`),
  KEY `FKcr2l2f799b2b3dcos129bnlcp` (`wallet_id`),
  CONSTRAINT `FK1a2vlfsh855qqog0j6dp4h6aw` FOREIGN KEY (`currency_id`) REFERENCES `currency` (`id`),
  CONSTRAINT `FKcr2l2f799b2b3dcos129bnlcp` FOREIGN KEY (`wallet_id`) REFERENCES `wallet` (`id`),
  CONSTRAINT `FKjddot67dfkn2kd7cg7cs39a3u` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
