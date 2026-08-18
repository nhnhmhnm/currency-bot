import pytest

from banks.shinhan import ShinhanCrawler
from utils.enums import Currency


# 환율을 제대로 가져왔는지 확인
@pytest.mark.integration
def test_fetch_shinhan_jpy_rate():
    exchange_rates = ShinhanCrawler().fetch_rates()
    jpy_rate = next((rate for rate in exchange_rates if rate.currency == Currency.JPY), None)

    if jpy_rate:
        assert jpy_rate.bank.value == "088"
        assert jpy_rate.currency == Currency.JPY
