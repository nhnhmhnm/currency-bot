from datetime import datetime
from decimal import Decimal

import requests

from dto.exchange_rate_dto import ExchangeRateDto
from interfaces.bank_interface import BankCrawler
from utils.enums import Bank, Currency


class ScCrawler(BankCrawler):
    def get_bank(self) -> Bank:
        return Bank.SC

    def fetch_rates(self) -> list[ExchangeRateDto]:
        datetime_format = "%Y%m%d%H%M%S"
        url = "https://www.standardchartered.co.kr/np/kr/pl/et/selectExchangeRateList"
        today = datetime.now().replace(microsecond=0)

        payload = {
            "serviceID": "HP_FP_PR_ExchangeRate.selectExchangeRateList",
            "task": "com.scfirstbank.web.fp.pr.task.HP_FP_PR_ExchangeRateTask",
            "action": "selectExchangeRateList",
            "TYPE_CD": "1",
            "CNT_TYPE_CD": "1",
            "CUR_YEAR": today.strftime("%Y"),
            "CUR_MONTH": today.strftime("%m"),
            "CUR_DAY": today.strftime("%d"),
        }

        try:
            response = requests.post(url, json=payload, timeout=10)
            response.raise_for_status()
            data = response.json()

            targets = {currency.value for currency in Currency}
            exchange_rates: list[ExchangeRateDto] = []

            for rates in data.get("vector", []):
                raw = rates.get("TYPE_A_RESULT", {})
                code = raw.get("CURRENCY")

                if code == "100JPY":
                    code = "JPY"

                if code in targets:
                    try:
                        timestamp = datetime.strptime(raw["DATETIME"], datetime_format)

                        exchange_rates.append(
                            ExchangeRateDto(
                                bank=Bank.SC,
                                currency=Currency(code),
                                base_rate=Decimal(raw["TMP_RATE"].replace(",", "")),
                                buy_rate=Decimal(raw["TTSALERATE"].replace(",", "")),
                                sell_rate=Decimal(raw["TTBUYRATE"].replace(",", "")),
                                notice_time=timestamp,
                                created_at=today,
                            )
                        )

                    except Exception as e:
                        print(f"[WARN] SC제일은행 {code} 환율 파싱 실패: {e}")
                        continue

            return exchange_rates

        except Exception as e:
            print(f"[ERROR] ScCrawler 실패: {e}")

            return []
