from banks.hana import HanaCrawler
from banks.ibk import IBKCrawler
from banks.sc import ScCrawler
from banks.shinhan import ShinhanCrawler
from interfaces.bank_interface import BankCrawler
from utils.db_connection import insert_rate

crawlers: list[BankCrawler] = [
    HanaCrawler(),
    IBKCrawler(),
    ScCrawler(),
    ShinhanCrawler(),
]


def crawl_all() -> None:
    for crawler in crawlers:
        try:
            results = crawler.fetch_rates()

            if not results:
                print(f"[WARN] {crawler.get_bank().value}: 크롤링 실패")
                continue

            for dto in results:
                insert_rate(dto)
                print(f"[SUCCESS] {dto.bank.value} {dto.currency.value} 저장 완료")

        except Exception as e:
            print(f"[ERROR] {crawler.get_bank().value} 처리 중 예외 발생: {e}")
