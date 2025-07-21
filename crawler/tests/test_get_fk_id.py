import mysql.connector
from config import DB_CONFIG
from utils.DBconnect import get_foreign_key_id
import pytest

# FK id 찾은 경우
def test_get_fk_id_found():
    with mysql.connector.connect(**DB_CONFIG) as conn:
        cursor = conn.cursor()
        bank_id = get_foreign_key_id(cursor, "banks", "IBK")
        assert isinstance(bank_id, int)

# FK id 못찾은 경우
def test_get_fk_id_not_found():
    with mysql.connector.connect(**DB_CONFIG) as conn:
        cursor = conn.cursor()

        with pytest.raises(ValueError):
            get_foreign_key_id(cursor, "banks", "QQQ")
            