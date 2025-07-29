from enum import Enum

class Bank(str, Enum):
    HANA = "HANA"
    IBK = "IBK"
    KB = "KB"
    NH = "NH"
    SC = "SC"
    SHINHAN = "SHINHAN"
    WOORI = "WOORI"
    
class Currency(str, Enum):
    USD = "USD"
    JPY = "JPY"
