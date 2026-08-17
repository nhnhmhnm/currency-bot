import pytest

from banks.shinhan import ShinhanCrawler
from utils.enums import Currency


# 환율을 제대로 가져왔는지 확인
@pytest.mark.integration
def test_fetch_shinhan_jpy_rate():
    results = ShinhanCrawler().fetch_rates()
    result = next((rate for rate in results if rate.currency == Currency.JPY), None)

    if result:
        assert result.bank.value == "088"
        assert result.currency == Currency.JPY
