from utils.crawler import crawl_all

if __name__ == "__main__":
    if crawl_all() == 0:
        raise SystemExit(1)
