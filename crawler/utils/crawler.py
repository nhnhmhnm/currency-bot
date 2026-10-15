from banks.hana import HanaCrawler
from banks.ibk import IBKCrawler
from banks.sc import ScCrawler
from banks.shinhan import ShinhanCrawler
from interfaces.bank_interface import BankCrawler
from utils.db_connection import insert_rate

BANK_CRAWLERS: list[BankCrawler] = [
    HanaCrawler(),
    IBKCrawler(),
    ScCrawler(),
    ShinhanCrawler(),
]


def crawl_all() -> int:
    stored = 0
    for crawler in BANK_CRAWLERS:
        try:
            exchange_rates = crawler.fetch_rates()

            if not exchange_rates:
                print(f"[WARN] {crawler.get_bank().value}: 크롤링 실패")
                continue

            for exchange_rate in exchange_rates:
                insert_rate(exchange_rate)
                stored += 1
                print(f"[SUCCESS] {exchange_rate.bank.value} {exchange_rate.currency.value} 저장 완료")

        except Exception as e:
            print(f"[ERROR] {crawler.get_bank().value} 처리 중 예외 발생: {e}")

    return stored
