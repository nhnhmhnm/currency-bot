import requests
from bs4 import BeautifulSoup
from datetime import datetime
from decimal import Decimal

from utils.enums import Bank, Currency
from utils.notice_time import parse_notice_time
from dto.exchange_rate_dto import ExchangeRateDTO
from interfaces.bank_interface import BankCrawler


class IBKCrawler(BankCrawler):
    def get_bank(self) -> Bank:
        return Bank.IBK

    def get_datas(self) -> list[ExchangeRateDTO]:
        url = "https://www.ibk.co.kr/fxtr/excRateList.ibk?pageId=SM03020100"
        today = datetime.now().replace(microsecond=0)
        results: list[ExchangeRateDTO] = []

        try:
            response = requests.get(url, headers={
                "User-Agent": "Mozilla/5.0"
            }, timeout=10)
            response.raise_for_status()

            soup = BeautifulSoup(response.text, "html.parser")

            # 고시 시각 파싱
            try:
                time_tag = soup.find("p", class_="standard")
                time_text = time_tag.text.strip()
                timestamp = parse_notice_time(time_text)
            except Exception as e:
                print(f"[WARN] IBK 고시 시각 파싱 실패: {e}")
                timestamp = today

            # 환율 테이블 파싱
            table = soup.find("table", class_="tbl_basic")
            rows = table.find_all("tr")

            for row in rows:
                try:
                    ths = row.find_all("th")
                    if not ths:
                        continue

                    code_text = ths[0].text.strip().upper()

                    try:
                        currency = Currency(code_text)
                    except ValueError:
                        continue  # 등록되지 않은 통화 무시

                    tds = row.find_all("td")
                    if len(tds) < 3:
                        continue

                    base_rate = Decimal(tds[0].text.strip().replace(",", ""))
                    buy_rate = Decimal(tds[2].text.strip().replace(",", ""))
                    sell_rate = Decimal(tds[1].text.strip().replace(",", ""))

                    dto = ExchangeRateDTO(
                        bank=Bank.IBK,
                        currency=currency,
                        base_rate=base_rate,
                        buy_rate=sell_rate,
                        sell_rate=buy_rate,
                        notice_time=timestamp,
                        created_at=today
                    )
                    results.append(dto)
                except Exception as e:
                    print(f"[WARN] IBK {code_text} 환율 파싱 실패: {e}")
                    continue

        except Exception as e:
            print(f"[ERROR] IBK 크롤링 실패: {e}")

        return results
