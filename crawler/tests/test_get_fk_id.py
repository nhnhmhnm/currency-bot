import mysql.connector
import pytest

from utils.db_connection import get_foreign_key_id

pytestmark = pytest.mark.integration

try:
    from config import DB_CONFIG
except ModuleNotFoundError:
    DB_CONFIG = None


# FK id 찾은 경우
def test_get_foreign_key_id_found():
    if DB_CONFIG is None:
        pytest.skip("config.py에 DB_CONFIG가 설정되어 있지 않습니다.")

    with mysql.connector.connect(**DB_CONFIG) as conn:
        cursor = conn.cursor()
        bank_id = get_foreign_key_id(cursor, "bank", "003")
        assert isinstance(bank_id, int)


# FK id 못찾은 경우
def test_get_foreign_key_id_not_found():
    if DB_CONFIG is None:
        pytest.skip("config.py에 DB_CONFIG가 설정되어 있지 않습니다.")

    with mysql.connector.connect(**DB_CONFIG) as conn:
        cursor = conn.cursor()

        with pytest.raises(ValueError):
            get_foreign_key_id(cursor, "bank", "QQQ")
