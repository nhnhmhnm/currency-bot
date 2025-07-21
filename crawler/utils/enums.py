from enum import Enum

class Bank(str, Enum):
    IBK = "IBK"
    SHINHAN = "SHINHAN"
    HANA = "HANA"
    SC = "SC"
    WOORI = "WOORI"
    KB = "KB"
    NH = "NH"
    
class Currency(str, Enum):
    USD = "USD"
    JPY = "JPY"
