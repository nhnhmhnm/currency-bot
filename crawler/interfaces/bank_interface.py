from typing import Protocol
from utils.enums import Bank
from dto.exchange_rate_dto import ExchangeRateDTO

class BankCrawler(Protocol):
    def get_bank(self) -> Bank:
        ...

    def get_datas(self) -> ExchangeRateDTO:
        ...