from decimal import Decimal
from dataclasses import dataclass
from datetime import datetime
from utils.enums import Bank, Currency

@dataclass
class ExchangeRateDTO:
    bank: Bank
    currency: Currency
    base_rate: Decimal 
    buy_rate: Decimal 
    sell_rate: Decimal 
    notice_time: datetime
    created_at: datetime
