from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from config import CHROME_DRIVER_PATH
from datetime import datetime

from utils.enums import Bank, Currency
from utils.notice_time import parse_notice_time
from dto.exchange_rate_dto import ExchangeRateDTO

def get_ibk(currency: Currency) -> ExchangeRateDTO | None:
    url = "https://www.ibk.co.kr/fxtr/excRateList.ibk?pageId=SM03020100"

    options = Options()
    options.add_argument("--headless")
    
    driver = None 

    try:
        service = Service(executable_path=CHROME_DRIVER_PATH)
        driver = webdriver.Chrome(service=service, options=options)

        driver.get(url)

        # 고시완료 시각
        try:
            time_tag = driver.find_element(By.CLASS_NAME, "standard")
            time_text = time_tag.text.strip()
            print(f"[INFO] IBK {time_text}")

            # 파싱
            timestamp = parse_notice_time(time_text)

        except Exception as e:
            timestamp = datetime.now()
            print(f"[WARN] IBK 고시완료 시각 파싱 실패 {e}")

        # 매매기준율
        table = driver.find_element(By.CLASS_NAME, "tbl_basic")
        rows = table.find_elements(By.TAG_NAME, "tr")
        print(f"[INFO] IBK 테이블 로드 성공")

        for row in rows:
            ths = row.find_elements(By.TAG_NAME, "th") # 통화

            if ths and currency.value in ths[0].text:
                tds = row.find_elements(By.TAG_NAME, "td") # 매매기준율

                if len(tds) >= 1:
                    rate_text = tds[0].text.strip().replace(",", "")
                    rate = float(rate_text)
                    print(f"[INFO] IBK {currency.value} 매매기준율: {rate}")

                    return {
                        "bank": Bank.IBK,
                        "currency": currency,
                        "rate": rate,
                        "timestamp": timestamp
                    }

        print(f"[WARN] IBK {currency.value} 데이터를 찾지 못함")

    except Exception as e:
        print(f"[ERROR] get_ibk 실패: {e}")

    finally:
        if driver: driver.quit()
