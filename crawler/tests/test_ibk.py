import pytest

from banks.ibk import IBKCrawler
from utils.enums import Currency


# 환율을 제대로 가져왔는지 확인
@pytest.mark.integration
def test_fetch_ibk_usd_rate():
    results = IBKCrawler().fetch_rates()
    result = next((rate for rate in results if rate.currency == Currency.USD), None)

    if result:
        assert result.bank.value == "003"
        assert result.currency == Currency.USD
