import mysql.connector
from config import DB_CONFIG
from datetime import datetime
from utils.enums import Bank, Currency

def get_fk_id(cursor, table: str, code: str) -> int:
    sql = f"SELECT id FROM {table} WHERE code = %s"
    cursor.execute(sql, (code,))
    result = cursor.fetchone()

    if result is None:
        raise ValueError(f"{table}에서 code='{code}'를 찾을 수 없습니다.")
    return result[0]

def insert_rate(bank: Bank, currency: Currency, base_rate: float, buy_rate: float, sell_rate: float, timestamp, created_at: datetime):
    try:
        with mysql.connector.connect(**DB_CONFIG) as conn:
            with conn.cursor() as cursor:
                # bank_id, currency_id 조회
                bank_id = get_fk_id(cursor, "banks", bank.value)
                currency_id = get_fk_id(cursor, "currencies", currency.value)

                created_at = datetime.now()

                # INSERT
                sql = """
                    INSERT INTO exchange_rates (bank_id, currency_id, base_rate, buy_rate, sell_rate, timestamp, created_at)
                    VALUES (%s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE rate = VALUES(rate)
                """
                cursor.execute(sql, (bank_id, currency_id, base_rate, buy_rate, sell_rate, timestamp, created_at))
                conn.commit()

                print(f"[DB] 저장 성공 : {bank.value} {currency.value} {base_rate} {created_at}")

    except Exception as e:
        print(f"[DB ERROR] {e}")
