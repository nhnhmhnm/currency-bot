import requests
from bs4 import BeautifulSoup
from datetime import datetime
from enum import Enum
from utils.enums import Currency, Bank
from interfaces.bank_interface import BankCrawler
from dto.exchange_rate_dto import ExchangeRateDTO

DATETIME_FORMAT = "%Y-%m-%d %H:%M:%S"

URL = "https://biz.kebhana.com/foex/rate/wcfxd740_101i_01.do"

class HanaCrawler(BankCrawler):
    def get_bank(self) -> Bank:
        return Bank.HANA
    
    def get_datas(self) -> list[ExchangeRateDTO]:
        HEADERS = {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        }
        # 하나은행 기업
        today = datetime.now()

        PAYLOAD = {
            "ajax": "true",
            "keyEncData": "",
            "inqStrDt": today.strftime("%Y%m%d"),
            "inqEndDt": today.strftime("%Y%m%d"),
            "inqKindCd": "1",
            "tmpDt": today.strftime("%Y%m%d"),
            "pbldDvCd": "0",
            "naviMenuNo": "90507",
            "requestTarget": "resultDiv"
        }

        try:
            res = requests.post(URL, data=PAYLOAD, headers=HEADERS)
            res.raise_for_status()
            data = BeautifulSoup(res.text, "html.parser")

            # 고시시간
            timestamp_str = None
            for th in data.select("table.row_type th"):
                if "고시일시" in th.get_text(strip=True):
                    td = th.find_next_sibling("td")
                    if td:
                        timestamp_str = td.get_text(strip=True)
                        break

            if not timestamp_str:
                raise ValueError("고시일시를 찾을 수 없습니다.")

            timestamp = datetime.strptime(timestamp_str, "%Y-%m-%d %H:%M:%S")
            
            targets = set(c.value for c in Currency) # rates에서 찾아야 할 target 통화
            
            dto = [] # 저장할 리스트

            rows = data.select("table.col_type tbody tr")

            for row in rows:
                cols = row.find_all("td")

                if len(cols) < 9:
                    continue  # 불완전한 row

                code = row.select_one("th").text.strip() # 통화 코드
                
                if code in targets:
                    try:
                        base_rate = float(cols[2].text.replace(",", ""))
                        buy_rate = float(cols[7].text.replace(",", ""))
                        sell_rate = float(cols[8].text.replace(",", ""))

                        dto.append(ExchangeRateDTO(
                            bank = Bank.HANA,
                            currency = Currency(code),
                            base_rate = base_rate,
                            buy_rate = buy_rate,
                            sell_rate = sell_rate,
                            timestamp = timestamp,
                            created_at = today.now().replace(microsecond=0)
                        ))

                    except Exception as e:
                        print(f"[WARN] 하나은행 {code} 환율 파싱 실패: {e}")
                        continue

            return dto

        except Exception as e:
            print(f"[ERROR] HanaCrawler 실패: {e}")
            
            return []


# <tr>
#     <th scope="row">USD</th>                         <!-- [0] 통화코드 -->
#     <td>달러(미국)</td>                              <!-- [1] 통화명 -->
#     <td class="r">1,380.50</td>                      <!-- [2] 매매기준율 (base_rate) -->
#     <td class="r">1,404.65</td>                      <!-- [3] 현찰매도율 -->
#     <td class="r">1.75%</td>                         <!-- [4] 현찰매도 마진율 -->
#     <td class="r">1,356.35</td>                      <!-- [5] 현찰매입율 -->
#     <td class="r">1.75%</td>                         <!-- [6] 현찰매입 마진율 -->
#     <td class="r">1,394.00</td>                      <!-- [7] 전신환매도율 (buy_rate) -->
#     <td class="r">1,367.00</td>                      <!-- [8] 전신환매입율 (sell_rate) -->
#     ...
# </tr>