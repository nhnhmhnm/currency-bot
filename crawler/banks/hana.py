import requests
from bs4 import BeautifulSoup
from datetime import datetime
from decimal import Decimal

from interfaces.bank_interface import BankCrawler
from dto.exchange_rate_dto import ExchangeRateDTO
from utils.enums import Bank, Currency


class HanaCrawler(BankCrawler):
    def get_bank(self) -> Bank:
        return Bank.HANA

    def get_datas(self) -> list[ExchangeRateDTO]:
        today = datetime.now().replace(microsecond=0)

        URL = "https://www.kebhana.com/cms/rate/wpfxd651_01i_01.do"

        HEADERS = {
            "User-Agent": "Mozilla/5.0",
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "X-Requested-With": "XMLHttpRequest"
        }

        PAYLOAD = {
            "ajax": "true",
            "curCd": "",
            "tmpInqStrDt": today.strftime("%Y-%m-%d"),
            "pbldDvCd": "3",
            "pbldSqn": "",
            "inqStrDt": today.strftime("%Y%m%d"),
            "inqKindCd": "1",
            "hid_key_data": "",
            "hid_enc_data": "",
            "requestTarget": "searchContentDiv"
        }

        dto: list[ExchangeRateDTO] = []

        try:
            response = requests.post(URL, headers=HEADERS, data=PAYLOAD, timeout=10)
            response.encoding = "utf-8"

            # 오류 JSON 응답 여부 확인
            if response.text.strip().startswith("{") or response.text.strip().startswith("["):
                print("[ERROR] 하나은행에서 JSON 오류 응답 수신:", response.text)
                return []

            soup = BeautifulSoup(response.text, "html.parser")

            # 고시 시각 추출
            timestamp = self._parse_notice_time(soup)

            # 환율 테이블 추출
            table = soup.find("table", class_="tblBasic")

            if not table:
                print("[WARN] 환율 테이블이 없습니다.")
                return []
            
            rows = table.find_all("tr")

            targets = set(c.value for c in Currency)

            for row in rows:
                tds = row.find_all("td")
                if len(tds) < 9:
                    continue

                code_tag = row.select_one("td.tc > a")
                if not code_tag:
                    continue

                code_text = code_tag.text.strip()
                code_parts = code_text.split()

                code = code_parts[1]

                if code not in targets:
                    continue

                try:
                    base_rate = Decimal(tds[8].text.strip().replace(",", ""))
                    buy_rate = Decimal(tds[5].text.strip().replace(",", ""))
                    sell_rate = Decimal(tds[6].text.strip().replace(",", ""))

                    dto.append(ExchangeRateDTO(
                        bank=Bank.HANA,
                        currency=Currency(code),
                        base_rate=base_rate,
                        buy_rate=buy_rate,
                        sell_rate=sell_rate,
                        notice_time=timestamp,
                        created_at=today
                    ))
                except Exception as e:
                    print(f"[WARN] 하나은행 {code} 환율 파싱 실패: {e}")

        except Exception as e:
            print(f"[ERROR] HanaCrawler 실패: {e}")

        return dto

    def _parse_notice_time(self, soup: BeautifulSoup) -> datetime:
        try:
            txt_box = soup.select_one("p.txtRateBox span.fl")
            if not txt_box:
                print("[WARN] 고시 시각 정보 없음")
                return datetime.now().replace(microsecond=0)

            strong_tags = txt_box.find_all("strong")
            if len(strong_tags) >= 2:
                date_text = strong_tags[1].text.strip()   # '2025년07월29일'
                time_text = strong_tags[2].text.strip()   # '13시47분00초'

                date_str = date_text.replace("년", "-").replace("월", "-").replace("일", "")
                time_str = time_text.replace("시", ":").replace("분", ":").replace("초", "")
                
        except Exception as e:
            print(f"[WARN] 하나은행 고시 시각 파싱 실패: {e}")

        return datetime.now().replace(microsecond=0)
