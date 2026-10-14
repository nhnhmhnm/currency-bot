-- Apply only after step 5 and a verified backup. Do not infer historical lines from summary records.
-- Keep admission and workers stopped while the schema is added and the opening balances are recorded.
CREATE TABLE journal (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_journal_source (source_type, source_id)
);

CREATE TABLE journal_line (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    journal_id BIGINT NOT NULL,
    line_number INT NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    account_id BIGINT NOT NULL,
    currency_id BIGINT NOT NULL,
    debit DECIMAL(24,8) NOT NULL,
    credit DECIMAL(24,8) NOT NULL,
    balance_before DECIMAL(24,8) NOT NULL,
    balance_after DECIMAL(24,8) NOT NULL,
    UNIQUE KEY uk_journal_line_number (journal_id, line_number),
    KEY idx_journal_line_account (account_type, account_id, id),
    CONSTRAINT fk_journal_line_journal FOREIGN KEY (journal_id) REFERENCES journal(id)
);
