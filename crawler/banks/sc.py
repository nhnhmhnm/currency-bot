import requests
from decimal import Decimal
from datetime import datetime
from enum import Enum
from utils.enums import Currency, Bank
from interfaces.bank_interface import BankCrawler
from dto.exchange_rate_dto import ExchangeRateDTO

class ScCrawler(BankCrawler):
    def get_bank(self) -> Bank:
        return Bank.SC
    
    def get_datas(self) -> list[ExchangeRateDTO]:
        DATETIME_FORMAT = "%Y%m%d%H%M%S"

        URL = "https://www.standardchartered.co.kr/np/kr/pl/et/selectExchangeRateList"
        
        today = datetime.now().replace(microsecond=0)

        PAYLOAD = {
            "serviceID":"HP_FP_PR_ExchangeRate.selectExchangeRateList",
            "task":"com.scfirstbank.web.fp.pr.task.HP_FP_PR_ExchangeRateTask",
            "action":"selectExchangeRateList",
            "TYPE_CD":"1",
            "CNT_TYPE_CD":"1",
            "CUR_YEAR": today.strftime("%Y"),
            "CUR_MONTH": today.strftime("%m"),
            "CUR_DAY": today.strftime("%d")
        }

        try:
            res = requests.post(URL, json=PAYLOAD)
            res.raise_for_status()
            data = res.json()
        
            targets = set(c.value for c in Currency) # rates에서 찾아야 할 target 통화
            
            dto: list[ExchangeRateDTO] = []

            for rates in data.get("vector", []):
                raw = rates.get("TYPE_A_RESULT", {})
                code = raw.get("CURRENCY")

                if code == "100JPY":
                    code = "JPY"

                if code in targets:
                    try:
                        timestamp = datetime.strptime(raw["DATETIME"], DATETIME_FORMAT)
                        
                        dto.append(ExchangeRateDTO(
                            bank = Bank.SC,
                            currency = Currency(code),
                            base_rate = Decimal(raw["TMP_RATE"].replace(",", "")),
                            buy_rate = Decimal(raw["TTSALERATE"].replace(",", "")),
                            sell_rate = Decimal(raw["TTBUYRATE"].replace(",", "")),
                            notice_time = timestamp,
                            created_at = today
                        ))

                    except Exception as e:
                        print(f"[WARN] SC제일은행 {code} 환율 파싱 실패: {e}")
                        continue

            return dto
        

        except Exception as e:
            print(f"[ERROR] ScCrawler 실패: {e}")
            
            return []
