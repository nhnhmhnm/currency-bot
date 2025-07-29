from utils.DBconnect import insert_rate
from interfaces.bank_interface import BankCrawler
from banks.shinhan import ShinhanCrawler
from banks.ibk import IBKCrawler
from banks.sc import ScCrawler
from banks.hana import HanaCrawler

crawlers: list[BankCrawler] = [
    HanaCrawler(),
    IBKCrawler(),
    ScCrawler(),
    ShinhanCrawler()
]

def crawl_all():
    for crawler in crawlers:
        try:
            results = crawler.get_datas() # list[ExchangeRateDTO]

            if not results:
                print(f"[WARN] {crawler.get_bank().value}: 크롤링 실패")
                continue

            for dto in results:
                insert_rate(dto)
                print(f"[SUCCESS] {dto.bank.value} {dto.currency.value} 저장 완료")

        except Exception as e:
            print(f"[ERROR] {crawler.get_bank().value} 처리 중 예외 발생: {e}")