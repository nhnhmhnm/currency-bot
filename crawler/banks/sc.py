import requests
from datetime import datetime
from enum import Enum
from utils.enums import Currency, Bank
from interfaces.bank_interface import BankCrawler
from dto.exchange_rate_dto import ExchangeRateDTO

DATETIME_FORMAT = "%Y%m%d%H%M%S"

class ScCrawler(BankCrawler):
    def get_bank(self) -> Bank:
        return Bank.SC
    
    def get_datas(self) -> list[ExchangeRateDTO]:
        url = "https://www.standardchartered.co.kr/np/kr/pl/et/selectExchangeRateList"
        
        today = datetime.now()

        payload = {
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
            res = requests.post(url, json=payload)
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
                            base_rate = float(raw["TMP_RATE"]),
                            buy_rate = float(raw["TTSALERATE"]),
                            sell_rate = float(raw["TTBUYRATE"]),
                            timestamp = timestamp,
                            created_at = datetime.now().replace(microsecond=0)
                        ))

                    except Exception as e:
                        print(f"[WARN] SC제일은행 {code} 환율 파싱 실패: {e}")
                        continue

            return dto
        

        except Exception as e:
            print(f"[ERROR] ScCrawler 실패: {e}")
            
            return []
