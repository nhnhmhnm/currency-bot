-- user 관련 테이블
CREATE TABLE user (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      device VARCHAR(100) UNIQUE NOT NULL,
                      password VARCHAR(255) NOT NULL,
                      name VARCHAR(50) NOT NULL,
                      phone VARCHAR(20) NOT NULL,
                      type ENUM('super', 'general') NOT NULL,
                      isActive BOOLEAN,
                      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE account (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         user_id BIGINT NOT NULL,
                         bank_id BIGINT NOT NULL,
                         currency_id BIGINT NOT NULL,
                         account_num VARCHAR(50) NOT NULL,
                         balance DECIMAL(18,2) DEFAULT 0,
                         isActive BOOLEAN,
                         created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                         FOREIGN KEY (user_id) REFERENCES user(id),
                         FOREIGN KEY (bank_id) REFERENCES bank(id),
                         FOREIGN KEY (currency_id) REFERENCES currency(id)
);

CREATE TABLE wallet (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        currency_id BIGINT NOT NULL,
                        account_id BIGINT,
                        balance DECIMAL(18,2) DEFAULT 0,

                        FOREIGN KEY (user_id) REFERENCES user(id),
                        FOREIGN KEY (currency_id) REFERENCES currency(id)
);

CREATE TABLE DepositOfWallet (
                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 user_id BIGINT NOT NULL,
                                 wallet_id BIGINT NOT NULL,
                                 currency_id BIGINT NOT NULL,
                                 amount DECIMAL(18,2) NOT NULL,
                                 status ENUM('PENDING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING',
                                 requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 executed_at DATETIME,

                                 FOREIGN KEY (user_id) REFERENCES user(id),
                                 FOREIGN KEY (wallet_id) REFERENCES wallet(id),
                                 FOREIGN KEY (currency_id) REFERENCES currency(id)
);

CREATE TABLE WithdrawalOfWallet (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    user_id BIGINT NOT NULL,
                                    wallet_id BIGINT NOT NULL,
                                    user_account_id BIGINT NOT NULL,
                                    currency_id BIGINT NOT NULL,
                                    amount DECIMAL(18,2) NOT NULL,
                                    status ENUM('PENDING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING',
                                    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    executed_at DATETIME,

                                    FOREIGN KEY (user_id) REFERENCES user(id),
                                    FOREIGN KEY (wallet_id) REFERENCES wallet(id),
                                    FOREIGN KEY (user_account_id) REFERENCES account(id),
                                    FOREIGN KEY (currency_id) REFERENCES currency(id)
);

CREATE TABLE wallet_history (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                wallet_id BIGINT NOT NULL,
                                currency_id BIGINT NOT NULL,
                                order_id BIGINT,
                                amount DECIMAL(18,2) NOT NULL,
                                balance_after DECIMAL(18,2) NOT NULL,
                                type ENUM('DEPOSIT', 'WITHDRAWAL', 'FX_BUY', 'FX_SELL', 'FEE', 'REWARD', 'REFUND', 'OTHER') NOT NULL,
                                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                                FOREIGN KEY (wallet_id) REFERENCES wallet(id),
                                FOREIGN KEY (currency_id) REFERENCES currency(id)
);

-- 은행/통화/환율 관련
CREATE TABLE bank (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      code VARCHAR(20) UNIQUE NOT NULL,
                      name VARCHAR(100) NOT NULL
);

CREATE TABLE currency (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          code VARCHAR(10) UNIQUE NOT NULL,
                          name VARCHAR(50) NOT NULL
);

CREATE TABLE exchange_rate (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               bank_id BIGINT NOT NULL,
                               currency_id BIGINT NOT NULL,
                               base_rate DECIMAL(12,6) NOT NULL,
                               buy_rate DECIMAL(12,6),
                               sell_rate DECIMAL(12,6),
                               notice_time DATETIME NOT NULL,
                               created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                               FOREIGN KEY (bank_id) REFERENCES bank(id),
                               FOREIGN KEY (currency_id) REFERENCES currency(id)
);

-- 주문/거래 관련
CREATE TABLE `order` (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         user_id BIGINT NOT NULL,
                         bank_id BIGINT NOT NULL,
                         from_currency_id BIGINT NOT NULL,
                         to_currency_id BIGINT NOT NULL,
                         from_amount DECIMAL(18,2) NOT NULL,
                         to_amount DECIMAL(18,2),
                         exchange_rate DECIMAL(18,6),
                         exchange_rate_id BIGINT NOT NULL,
                         status ENUM('PENDING', 'SUCCESS', 'FAILED') NOT NULL,
                         ordered_at DATETIME NOT NULL,
                         executed_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                         FOREIGN KEY (user_id) REFERENCES user(id),
                         FOREIGN KEY (bank_id) REFERENCES bank(id),
                         FOREIGN KEY (from_currency_id) REFERENCES currency(id),
                         FOREIGN KEY (to_currency_id) REFERENCES currency(id),
                         FOREIGN KEY (exchange_rate_id) REFERENCES exchange_rate(id)
);

CREATE TABLE transactions (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              user_id BIGINT NOT NULL,
                              wallet_id BIGINT NOT NULL,
                              order_id BIGINT NOT NULL,
                              from_currency_id BIGINT NOT NULL,
                              to_currency_id BIGINT NOT NULL,
                              from_amount DECIMAL(18,2) NOT NULL,
                              to_amount DECIMAL(18,2) NOT NULL,
                              exchange_rate DECIMAL(18,6),
                              commission_rate DECIMAL(5,4),
                              commission_amount DECIMAL(18,2),
                              commission_currency_id BIGINT,
                              profit DECIMAL(18,2),
                              profit_currency_id BIGINT,
                              created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                              FOREIGN KEY (user_id) REFERENCES user(id),
                              FOREIGN KEY (wallet_id) REFERENCES wallet(id),
                              FOREIGN KEY (order_id) REFERENCES `order`(id),
                              FOREIGN KEY (from_currency_id) REFERENCES currency(id),
                              FOREIGN KEY (to_currency_id) REFERENCES currency(id),
                              FOREIGN KEY (commission_currency_id) REFERENCES currency(id),
                              FOREIGN KEY (profit_currency_id) REFERENCES currency(id)
);

CREATE TABLE exchange_ledger (
                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 user_id BIGINT NOT NULL,
                                 wallet_id BIGINT NOT NULL,
                                 currency_id BIGINT NOT NULL,
                                 amount DECIMAL(18,2) NOT NULL,
                                 balance DECIMAL(18,2) NOT NULL,
                                 exchange_rate DECIMAL(18,6),
                                 commission_amount DECIMAL(18,2),
                                 commission_rate DECIMAL(5,4),
                                 commission_currency_id BIGINT,
                                 type ENUM('DEPOSIT', 'WITHDRAWAL', 'COMISSION', 'ETC') NOT NULL,
                                 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                                 FOREIGN KEY (user_id) REFERENCES user(id),
                                 FOREIGN KEY (wallet_id) REFERENCES wallet(id),
                                 FOREIGN KEY (currency_id) REFERENCES currency(id),
                                 FOREIGN KEY (commission_currency_id) REFERENCES currency(id)
);