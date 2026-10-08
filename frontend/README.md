# Currency Bot Frontend

React + TypeScript + Vite. Node.js 22.12 이상을 사용한다.

```powershell
cd frontend
npm ci
npm run dev
npm run build
npm run test:e2e
```

- 개발 서버는 기본적으로 `http://127.0.0.1:5173`에서 실행된다. 포트 사용 중에는 Vite가 다음 포트를 선택한다.
- `/api`는 Spring Boot `http://127.0.0.1:8080`으로 프록시한다. 다른 주소는 `.env.local`의 `BACKEND_URL`로 지정한다.
- 로그인 아이디는 기존 백엔드의 `device` 필드에 대응한다. 회원가입은 GENERAL 유형을 요청한다.
- 토큰은 sessionStorage에 보관한다. 운영 환경은 HTTPS, CSP, 서버가 발급하는 HttpOnly 쿠키 기반 세션 등 별도 보안 검토가 필요하다.
- 배포 시 정적 빌드와 같은 출처의 `/api` 리버스 프록시를 구성한다. Vite 개발 프록시는 빌드 결과에 포함되지 않는다.
- 브라우저 테스트는 설치된 Chrome을 사용하며 API를 모의 응답한다. 실제 DB·Redis·백엔드 연결 검증과 구분한다.
