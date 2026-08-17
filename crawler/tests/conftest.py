from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service

import pytest

try:
    from config import CHROME_DRIVER_PATH
except ModuleNotFoundError:
    CHROME_DRIVER_PATH = None


def pytest_addoption(parser):
    parser.addoption(
        "--run-integration",
        action="store_true",
        default=False,
        help="run tests that call live external services",
    )


def pytest_collection_modifyitems(config, items):
    if config.getoption("--run-integration"):
        return

    skip_integration = pytest.mark.skip(reason="--run-integration 옵션이 필요합니다.")
    for item in items:
        if "integration" in item.keywords:
            item.add_marker(skip_integration)


# 테스트 환경 준비 (브라우저 드라이버 객체 준비)
@pytest.fixture(scope="module")
def driver():
    if CHROME_DRIVER_PATH is None:
        pytest.skip("config.py에 CHROME_DRIVER_PATH가 설정되어 있지 않습니다.")

    options = Options()
    options.add_argument("--headless")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")

    service = Service(executable_path=CHROME_DRIVER_PATH)
    driver = webdriver.Chrome(service=service, options=options)
    yield driver

    driver.quit()
