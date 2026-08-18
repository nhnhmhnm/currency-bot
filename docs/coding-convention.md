# Coding Convention

이 프로젝트는 Python 크롤러와 Kotlin/Spring 백엔드가 함께 있으므로, 언어별로 널리 쓰이는 표준 컨벤션을 따른다.

## Python

Python 코드는 PEP 8을 기본으로 하며, 포맷팅은 Black 스타일을 따른다. 정적 분석과 import 정리는 Ruff 기준을 사용한다.

- 모듈, 패키지, 함수, 변수는 `snake_case`를 사용한다.
- 클래스는 `PascalCase`를 사용한다.
- 상수는 `UPPER_SNAKE_CASE`를 사용한다.
- Boolean 값은 `is_`, `has_`, `can_`처럼 의미가 드러나는 접두사를 우선한다.
- 함수명은 동작을 표현하는 동사형을 사용한다. 예: `fetch_rates`, `insert_rate`
- 파일명은 소문자 `snake_case`를 사용한다. 예: `db_connection.py`
- 최대 줄 길이는 88자를 기준으로 한다.
- import 순서는 표준 라이브러리, 서드파티 라이브러리, 프로젝트 내부 모듈 순서로 구분한다.
- DB, 네트워크, Selenium처럼 외부 환경에 의존하는 테스트는 `integration` 마커를 붙인다.

권장 명령:

```bash
python -m black crawler
python -m ruff check crawler
python -m pytest
python -m pytest --run-integration
```

## Kotlin

Kotlin 코드는 Kotlin 공식 코딩 컨벤션을 따른다. 에디터 설정은 `.editorconfig`의 `ij_kotlin_code_style_defaults = KOTLIN_OFFICIAL`을 기준으로 한다.

- 클래스, 인터페이스, enum class, data class는 `PascalCase`를 사용한다.
- 함수, 변수, property, parameter는 `camelCase`를 사용한다.
- 상수는 `UPPER_SNAKE_CASE`를 사용한다.
- 파일명은 대표 public class/object 이름과 맞춘다. 예: `BestExchangeRateDto.kt`
- 약어는 일반 단어처럼 취급한다. 예: `Dto`, `Url`, `Id`
- 확장 함수도 일반 함수처럼 `camelCase`를 사용한다. 예: `toDto`
- 불필요한 wildcard import는 피하고 명시적 import를 사용한다.
- nullable 값은 `!!`보다 `requireNotNull`, `?: throw`, 명시적 null 처리를 우선한다.
- 서비스/리포지토리 구현체 이름은 인터페이스 이름 뒤에 `Impl`을 붙인다. 예: `ExchangeRateJdbcRepositoryImpl`

권장 명령:

```bash
backend/gradlew -p backend clean compileKotlin testClasses
backend/gradlew -p backend test
```

## Common Rules

- `.editorconfig`를 기준으로 들여쓰기, 줄 끝, 최종 개행을 맞춘다.
- 생성 파일과 로컬 환경 파일은 Git에 포함하지 않는다.
- 이름 변경 리팩토링은 호출부, 테스트, 파일명까지 함께 변경한다.
- 외부 시스템이 필요한 테스트와 순수 단위 테스트는 분리한다.
- 새로운 규칙이 필요하면 이 문서와 도구 설정을 함께 갱신한다.
