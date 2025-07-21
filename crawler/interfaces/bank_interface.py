from typing import Protocol
from utils.enums import Bank

class BankCrawler(Protocol):
    def get_bank(self) -> Bank:
        ...

    def get_datas(self) -> list[dict]:
        ...