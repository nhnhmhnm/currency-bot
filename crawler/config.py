import os


password = os.getenv("CRAWLER_DB_PASSWORD")
DB_CONFIG = (
    {
        "host": os.getenv("CRAWLER_DB_HOST", "127.0.0.1"),
        "port": int(os.getenv("CRAWLER_DB_PORT", "3306")),
        "user": os.getenv("CRAWLER_DB_USER", "root"),
        "password": password,
        "database": os.getenv("CRAWLER_DB_NAME", "cuunit"),
    }
    if password
    else None
)

CHROME_DRIVER_PATH = os.getenv("CHROME_DRIVER_PATH")
