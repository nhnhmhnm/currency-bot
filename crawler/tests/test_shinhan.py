from banks.shinhan import get_shinhan
from utils.enums import Currency

# 환율을 제대로 가져왔는지 확인
def test_get_shinhan_USD():
    result = get_shinhan(Currency.JPY)

    if result:
        assert result["bank"] == "SHINHAN"
        assert result["currency"] == "JPY"
        assert isinstance(result["rate"], float)
