import sys
import pytest
from datetime import datetime
from decimal import Decimal
from types import ModuleType
from unittest.mock import MagicMock

from dto.exchange_rate_dto import ExchangeRateDto
from utils.db_connection import insert_rate
from utils.enums import Bank, Currency


def test_repeated_notice_updates_rate_values(monkeypatch):
    config = ModuleType("config")
    config.DB_CONFIG = {"database": "isolated-test"}
    monkeypatch.setitem(sys.modules, "config", config)

    cursor = MagicMock()
    cursor.fetchone.side_effect = [(1,), (2,)]
    connection = MagicMock()
    connection.__enter__.return_value = connection
    connection.cursor.return_value.__enter__.return_value = cursor
    monkeypatch.setattr("mysql.connector.connect", lambda **kwargs: connection)

    notice = datetime(2026, 10, 15, 12)
    insert_rate(ExchangeRateDto(
        Bank.SHINHAN, Currency.USD, Decimal("1300"), Decimal("1310"),
        Decimal("1290"), notice, notice
    ))

    sql, values = cursor.execute.call_args_list[-1].args
    assert "base_rate = VALUES(base_rate)" in sql
    assert "buy_rate = VALUES(buy_rate)" in sql
    assert "sell_rate = VALUES(sell_rate)" in sql
    assert values[:2] == (1, 2)
    connection.commit.assert_called_once()


def test_insert_rate_propagates_database_error(monkeypatch):
    config = ModuleType("config")
    config.DB_CONFIG = {"database": "isolated-test"}
    monkeypatch.setitem(sys.modules, "config", config)

    def fail_connect(**kwargs):
        raise RuntimeError("database unavailable")

    monkeypatch.setattr("mysql.connector.connect", fail_connect)
    notice = datetime(2026, 10, 15, 12)

    with pytest.raises(RuntimeError, match="database unavailable"):
        insert_rate(ExchangeRateDto(
            Bank.SHINHAN, Currency.USD, Decimal("1300"), Decimal("1310"),
            Decimal("1290"), notice, notice
        ))
