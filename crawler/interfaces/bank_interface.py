from typing import Protocol

from dto.exchange_rate_dto import ExchangeRateDto
from utils.enums import Bank


class BankCrawler(Protocol):
    def get_bank(self) -> Bank:
        ...

    def fetch_rates(self) -> list[ExchangeRateDto]:
        ...
