import mysql.connector
from config import DB_CONFIG
from dto.exchange_rate_dto import ExchangeRateDTO

def get_fk_id(cursor, table: str, code: str) -> int:
    sql = f"SELECT id FROM {table} WHERE code = %s"
    cursor.execute(sql, (code,))
    result = cursor.fetchone()

    if result is None:
        raise ValueError(f"{table}에서 code='{code}'를 찾을 수 없습니다.")
    
    return result[0]

def insert_rate(dto: ExchangeRateDTO):
    try:
        with mysql.connector.connect(**DB_CONFIG) as conn:
            with conn.cursor() as cursor:
                # 외래키 조회
                bank_id = get_fk_id(cursor, "bank", dto.bank.value)
                currency_id = get_fk_id(cursor, "currency", dto.currency.value)

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
                cursor.execute(sql, (
                    bank_id,
                    currency_id,
                    dto.base_rate,
                    dto.buy_rate,
                    dto.sell_rate,
                    dto.notice_time,
                    dto.created_at
                ))
                conn.commit()

                print(f"[DB] 저장 성공 : {dto.bank.value} {dto.currency.value} {dto.base_rate} {dto.created_at}")

    except Exception as e:
        print(f"[DB ERROR] {e}")
