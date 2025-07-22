from dataclasses import dataclass
from utils.enums import Bank, Currency
from datetime import datetime

@dataclass
class ExchangeRateDTO:
    bank: Bank
    currency: Currency
    base_rate: float
    buy_rate: float
    sell_rate: float
    timestamp: datetime
    created_at: datetime
