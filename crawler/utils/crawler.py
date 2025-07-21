from utils.enums import Bank, Currency
from utils.DBconnect import insert_rate
from interfaces.bank_interface import BankCrawler
from banks.shinhan import get_shinhan
from banks.ibk import get_ibk


crawlers: list[BankCrawler] = [
    get_shinhan()
]

def crawl_all():
    for crawler in crawlers:
        try:
            results = crawler.get_datas()

            if not results:
                print(f"[WARN] {crawler.get_bank().value}: 크롤링 실패")
                continue

            for result in results:
                insert_rate(**result)
                print(f"[SUCCESS] {result['bank'].value} {result['currency'].value} 저장 완료")

        except Exception as e:
            print(f"[ERROR] {crawler.get_bank().value} 처리 중 예외 발생: {e}")