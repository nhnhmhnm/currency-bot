from types import SimpleNamespace

from utils import crawler as crawler_module


def test_crawl_all_continues_after_bank_failure(monkeypatch):
    def fail():
        raise RuntimeError("unavailable")

    rate = SimpleNamespace(
        bank=SimpleNamespace(value="working"),
        currency=SimpleNamespace(value="USD"),
    )
    failing = SimpleNamespace(
        get_bank=lambda: SimpleNamespace(value="failed"),
        fetch_rates=fail,
    )
    working = SimpleNamespace(
        get_bank=lambda: SimpleNamespace(value="working"),
        fetch_rates=lambda: [rate],
    )
    saved = []
    monkeypatch.setattr(crawler_module, "BANK_CRAWLERS", [failing, working])
    monkeypatch.setattr(crawler_module, "insert_rate", saved.append)

    assert crawler_module.crawl_all() == 1
    assert len(saved) == 1


def test_crawl_all_reports_no_success_when_save_fails(monkeypatch):
    def fail_save(rate):
        raise RuntimeError("db down")

    bank = SimpleNamespace(
        get_bank=lambda: SimpleNamespace(value="working"),
        fetch_rates=lambda: [object()],
    )
    monkeypatch.setattr(crawler_module, "BANK_CRAWLERS", [bank])
    monkeypatch.setattr(crawler_module, "insert_rate", fail_save)

    assert crawler_module.crawl_all() == 0
