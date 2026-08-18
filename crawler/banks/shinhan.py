from datetime import datetime
from decimal import Decimal

import requests

from dto.exchange_rate_dto import ExchangeRateDto
from interfaces.bank_interface import BankCrawler
from utils.enums import Bank, Currency


class ShinhanCrawler(BankCrawler):
    def get_bank(self) -> Bank:
        return Bank.SHINHAN

    def fetch_rates(self) -> list[ExchangeRateDto]:
        today = datetime.now().replace(microsecond=0)
        datetime_format = "%Y.%m.%d %H:%M:%S"
        url = "https://bank.shinhan.com/serviceEndpoint/httpDigital"

        payload = {
            "dataBody": {
                "ricInptRootInfo": {
                    "serviceType": "GU",
                    "serviceCode": "F3733",
                    "callBack": "shbObj.fncF3733Callback",
                    "webUri": "/index.jsp",
                    "isRule": "N",
                },
                "조회구분": "",
                "조회일자": today.strftime("%Y%m%d"),
                "고시회차": "",
            },
            "dataHeader": {
                "trxCd": "RSRFO0100A01",
                "language": "ko",
                "subChannel": "49",
                "channelGbn": "D0",
            },
        }

        try:
            response = requests.post(url, json=payload, timeout=10)
            response.raise_for_status()
            data = response.json()

            # 고시시간
            date_str = data["dataBody"]["고시일자_display"]
            time_str = data["dataBody"]["고시시간_display"]

            datetime_str = f"{date_str} {time_str}"
            timestamp = datetime.strptime(datetime_str, datetime_format)

            # 통화
            rates = data["dataBody"]["R_RIBF3733_1"]
            targets = {currency.value for currency in Currency}

            exchange_rates: list[ExchangeRateDto] = []

            for rate in rates:
                code = rate.get("통화CODE")

                try:
                    if code in targets:
                        exchange_rates.append(
                            ExchangeRateDto(
                                bank=Bank.SHINHAN,
                                currency=Currency(code),
                                base_rate=Decimal(rate["매매기준환율"]),
                                buy_rate=Decimal(rate["전신환매도환율"]),
                                sell_rate=Decimal(rate["전신환매입환율"]),
                                notice_time=timestamp,
                                created_at=today,
                            )
                        )

                except Exception as e:
                    print(f"[WARN] 신한은행 {code} 환율 파싱 실패: {e}")
                    continue

            return exchange_rates

        except Exception as e:
            print(f"[ERROR] ShinhanCrawler 실패: {e}")

            return []
