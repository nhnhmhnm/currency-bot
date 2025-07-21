import re
from datetime import datetime

# 고시완료 시각
# 성공하면 시각 파싱 후 저장, 실패하면 현재 시각 저장
def parse_notice_time(text: str) -> datetime:
    try:
        match = re.search(r"(\d{1,2}):(\d{2}):(\d{2})", text)
        if match:
            hour, minute, second = map(int, match.groups())
            return datetime.now().replace(hour=hour, minute=minute, second=second, microsecond=0)
        else:
            raise ValueError("형식 일치 실패")
        
    except Exception as e:
        print(f"[WARN] 고시 시각 파싱 실패, fallback 사용: {e}")
        return datetime.now()
