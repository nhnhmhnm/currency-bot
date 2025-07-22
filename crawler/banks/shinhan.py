import requests
from datetime import datetime
from enum import Enum
from utils.enums import Currency, Bank
from interfaces.bank_interface import BankCrawler
from dto.exchange_rate_dto import ExchangeRateDTO

class get_shinhan(BankCrawler):
    def get_bank(self) -> Bank:
        return Bank.SHINHAN
    
    def get_datas(self) -> ExchangeRateDTO:
        url = "https://bank.shinhan.com/serviceEndpoint/httpDigital"

        payload = {
            "dataBody": {
                "ricInptRootInfo": {
                    "serviceType": "GU",
                    "serviceCode": "F3733",
                    "callBack": "shbObj.fncF3733Callback",
                    "webUri": "/index.jsp",
                    "isRule": "N"
                },
                "조회구분": "",
                "조회일자": datetime.now().strftime("%Y%m%d"),
                "고시회차": ""
            },
            "dataHeader": {
                "trxCd": "RSRFO0100A01",
                "language": "ko",
                "subChannel": "49",
                "channelGbn": "D0"
            }
        }

        try:
            res = requests.post(url, json=payload)
            res.raise_for_status()
            data = res.json()
        
            # 고시시간
            date_str = data["dataBody"]["고시일자_display"]
            time_str = data["dataBody"]["고시시간_display"]
            datetime_str = f"{date_str} {time_str}"
            
            timestamp = datetime.strptime(datetime_str, "%Y.%m.%d %H:%M:%S")

            # 통화
            rates = data["dataBody"]["R_RIBF3733_1"] # request의 통화 정보 리스트
            targets = set(c.value for c in Currency) # rates에서 찾아야 할 target 통화
            dto = [] # 저장할 dto

            for rate in rates:
                code = rate["통화CODE"]
                
                if code in targets :
                    dto.append(ExchangeRateDTO(
                        bank=Bank.SHINHAN,
                        currency=Currency(code),
                        base_rate=float(rate["매매기준환율"]),
                        buy_rate=float(rate["전신환매도환율"]),
                        sell_rate=float(rate["전신환매입환율"]),
                        timestamp=timestamp,
                        created_at=datetime.now().replace(microsecond=0)
                    ))

            return dto

        except Exception as e:
            print(f"[ERROR] get_shinhan 실패: {e}")
            
            return []
