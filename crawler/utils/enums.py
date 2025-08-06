from enum import Enum

class Bank(str, Enum):
    IBK = "003"
    KB = "004"
    NH = "011"
    WOORI = "020"
    SC = "023"
    HANA = "081"
    SHINHAN = "088"
    
class Currency(str, Enum):
    KRW = "KRW"
    USD = "USD"
    JPY = "JPY"
