from utils.notice_time import parse_notice_time
from datetime import datetime

def test_parse_notice_time():
    text = """
    고시완료 시각 : 20:25:53
    (00시 이후 환율은 조회기준일 익일 새벽에 고시됩니다.)
    """
    result = parse_notice_time(text)

    assert isinstance(result, datetime)
    assert result.hour == 20
    assert result.minute == 25
    assert result.second == 53
