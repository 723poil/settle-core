# settle-core

결제 승인, 취소, 정산 배치 생성 흐름을 Kotlin과 Spring Boot 기반으로 재구성하는 결제 정산 코어 프로젝트입니다.

기존에 다뤘던 결제/정산 도메인 지식을 Spring Boot, JPA, Flyway, PostgreSQL, Redis, Kafka 환경에 맞춰 다시 설계하고 있습니다. 단순 API 서버보다 도메인 경계, 데이터 정합성, 배치 실행, 외부 PG 연동 확장성을 함께 고려하는 것을 목표로 합니다.

## Stack

- Kotlin + Spring Boot 4
- Gradle Kotlin DSL multi-module
- Spring Data JPA
- Flyway
- PostgreSQL
- Redis
- Kafka
- Docker Compose
- Spotless + ktlint
- Kover
- mise-managed Java 21 and Kotlin

## Modules

```text
settle-core
├── apps
│   ├── api                 # REST API server
│   └── scheduler           # scheduled settlement runner
├── domains
│   ├── merchant
│   ├── payment
│   └── settlement
└── libs
    ├── common
    ├── infra
    ├── pg-client
    ├── persistence
    └── test-support
```

`apps/*`는 실행 진입점입니다. HTTP 요청 처리, 스케줄 실행, 앱별 설정처럼 런타임에 가까운 책임을 가집니다.

`domains/*`는 결제 정산 업무 규칙을 담는 모듈입니다. API나 스케줄러가 직접 비즈니스 규칙을 소유하지 않고 도메인 모듈을 통해 흐름을 실행하도록 구성합니다.

`libs/*`는 공통 타입과 기술 기반을 제공합니다. Redis/Kafka 연결 확인, JPA/Flyway 설정, PG 클라이언트 추상화처럼 여러 앱에서 공유되는 기술 요소를 분리합니다.

## Local Development

이 프로젝트는 `mise`로 Java/Kotlin 버전을 고정하고, Docker Compose로 PostgreSQL, Redis, Kafka를 실행합니다.

```bash
mise install
```

프로젝트에 진입하면 `mise` hook을 통해 로컬 인프라가 자동으로 실행됩니다. 직접 실행하거나 상태를 확인할 수도 있습니다.

```bash
mise run infra-up
mise run infra-ps
mise run infra-down
```

Flyway migration은 애플리케이션 시작 시 자동 실행하지 않고, root Gradle task를 통해 한 곳에서만 실행합니다.
같은 DB에 여러 앱이 붙어도 migration 실행 주체가 섞이지 않도록, 앱 실행 명령과 migration 명령은 분리합니다.

```bash
mise run flyway-info
mise run flyway-validate
mise run flyway-migrate
mise run flyway-repair
```

## Run Applications

API 서버 실행:

```bash
mise run api
```

API 서버를 개발 모드로 실행:

```bash
mise run api-dev
```

스케줄러 실행:

```bash
mise run scheduler
```

스케줄러를 개발 모드로 실행:

```bash
mise run scheduler-dev
```

`*-dev` 명령은 Spring Boot DevTools와 Gradle continuous build를 함께 사용해 Kotlin/리소스 변경 시 애플리케이션을 재시작합니다.

## Connection Settings

기본 로컬 연결값은 다음과 같습니다.

- PostgreSQL: `jdbc:postgresql://localhost:5432/settle_core`
- Redis: `localhost:6379`
- Kafka: `localhost:9092`

PostgreSQL schema는 `public`을 사용하지 않고 도메인별로 분리합니다. Flyway history는 `migration` schema에 두고, 도메인 테이블은 `merchant`, `payment`, `settlement` schema에 배치합니다.

설정 파일:

- `apps/api/src/main/resources/application.yml`
- `apps/scheduler/src/main/resources/application.yml`

주요 환경 변수:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `KAFKA_BOOTSTRAP_SERVERS`
- `KAFKA_CONSUMER_GROUP`
- `SCHEDULER_ENABLED`

애플리케이션 시작 시 `libs:infra`의 smoke check가 Redis `PING`과 Kafka broker discovery를 수행합니다. 연결에 성공하면 다음과 같은 로그가 남습니다.

```text
Redis connection smoke check succeeded: PONG
Kafka connection smoke check succeeded: 1 broker(s)
```

## Quality Checks

Payment 모듈 테스트 실행:

```bash
mise run test-payment
```

포맷 검사와 테스트 실행:

```bash
mise run check
```

테스트 커버리지 리포트 생성:

```bash
mise run coverage
```

HTML 리포트는 다음 경로에서 확인할 수 있습니다.

```text
build/reports/kover/html/index.html
```

CI 연동이나 외부 분석 도구가 필요할 때는 XML 리포트를 생성합니다.

```bash
mise run coverage-xml
```

```text
build/reports/kover/report.xml
```

현재 커버리지 수치를 콘솔에서 확인하거나 기준만 검증할 수도 있습니다.

```bash
mise run coverage-log
mise run coverage-verify
```

커버리지 측정은 Kotlin 코드 기준으로 Kover를 사용합니다. 수치를 높이기 위한 테스트보다 결제 유즈케이스, PG provider 라우팅, 서킷브레이커 동작처럼 실제 장애 대응과 도메인 흐름을 검증하는 테스트를 우선합니다.

커버리지 목표는 line/branch coverage 90%입니다. 빌드 검증은 각 항목이 85% 미만이면 실패하도록 설정합니다.

포맷 적용:

```bash
mise run format
```

## Architecture Notes

프로젝트 구조와 모듈 책임은 `docs/architecture/project-structure.md`에 정리되어 있습니다.
