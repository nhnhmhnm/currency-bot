import pytest

from banks.ibk import IBKCrawler
from utils.enums import Currency


# 환율을 제대로 가져왔는지 확인
@pytest.mark.integration
def test_fetch_ibk_usd_rate():
    exchange_rates = IBKCrawler().fetch_rates()
    usd_rate = next((rate for rate in exchange_rates if rate.currency == Currency.USD), None)

    if usd_rate:
        assert usd_rate.bank.value == "003"
        assert usd_rate.currency == Currency.USD
