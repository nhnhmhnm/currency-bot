-- Run once after clean-baseline-mysql94.sql in a new empty schema.
-- Currency IDs are part of CompanyAccount and must remain 1=KRW, 2=USD, 3=JPY.
INSERT INTO currency (id, code, name, `scale`, unit) VALUES
    (1, 'KRW', 'Korean Won', 0, 1),
    (2, 'USD', 'US Dollar', 2, 1),
    (3, 'JPY', 'Japanese Yen', 0, 100);

INSERT INTO bank (code, name) VALUES
    ('003', 'IBK Bank'),
    ('004', 'KB Kookmin Bank'),
    ('011', 'NH Bank'),
    ('020', 'Woori Bank'),
    ('023', 'SC First Bank'),
    ('081', 'Hana Bank'),
    ('088', 'Shinhan Bank');

INSERT INTO internal_account (code, currency_id, balance) VALUES
    ('COMMISSION_ACCOUNT', 1, 0),
    ('KRW_ACCOUNT', 1, 0),
    ('USD_ACCOUNT', 2, 0),
    ('JPY_ACCOUNT', 3, 0);
