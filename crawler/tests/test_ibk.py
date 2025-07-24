from selenium.webdriver.common.by import By
from utils.enums import Currency
from banks.ibk import get_ibk
import pytest

# 환율을 제대로 가져왔는지 확인
def test_get_ibk_USD():
    result = get_ibk(Currency.USD)

    if result:
        assert result["bank"] == "IBK"
        assert result["currency"] == "USD"
        assert isinstance(result["rate"], float)
