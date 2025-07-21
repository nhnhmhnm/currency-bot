import schedule
import time
from main import crawl_all

print("[시작] 환율 크롤러")

crawl_all()  # 최초 실행

# 10분마다 실행
schedule.every(10).minutes.do(crawl_all)

while True:
    schedule.run_pending()
    time.sleep(1)
