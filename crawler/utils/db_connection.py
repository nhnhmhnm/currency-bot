import mysql.connector

from dto.exchange_rate_dto import ExchangeRateDTO

REFERENCE_TABLES = {"bank", "currency"}


def get_foreign_key_id(cursor, table: str, code: str) -> int:
    if table not in REFERENCE_TABLES:
        raise ValueError(f"지원하지 않는 참조 테이블입니다: {table}")

    sql = f"SELECT id FROM {table} WHERE code = %s"
    cursor.execute(sql, (code,))
    result = cursor.fetchone()

    if result is None:
        raise ValueError(f"{table}에서 code='{code}'를 찾을 수 없습니다.")

    return result[0]


def insert_rate(dto: ExchangeRateDTO) -> None:
    from config import DB_CONFIG

    try:
        with mysql.connector.connect(**DB_CONFIG) as conn:
            with conn.cursor() as cursor:
                # 외래키 조회
                bank_id = get_foreign_key_id(cursor, "bank", dto.bank.value)
                currency_id = get_foreign_key_id(cursor, "currency", dto.currency.value)

                # INSERT
                sql = """
                    INSERT INTO exchange_rate (
                        bank_id, currency_id, base_rate, buy_rate, sell_rate, notice_time, created_at
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE
                        bank_id = VALUES(bank_id),
                        currency_id = VALUES(currency_id),
                        notice_time = VALUES(notice_time)
                """
                cursor.execute(
                    sql,
                    (
                        bank_id,
                        currency_id,
                        dto.base_rate,
                        dto.buy_rate,
                        dto.sell_rate,
                        dto.notice_time,
                        dto.created_at,
                    ),
                )
                conn.commit()

                print(
                    "[DB] 저장 성공 : "
                    f"{dto.bank.value} {dto.currency.value} "
                    f"{dto.base_rate} {dto.created_at}"
                )

    except Exception as e:
        print(f"[DB ERROR] {e}")
