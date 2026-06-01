# settle-core

Payment settlement core built with Kotlin, Spring Boot, JPA, Flyway, PostgreSQL, and Gradle multi-module boundaries.

## Stack

- Kotlin + Spring Boot 4
- Gradle Kotlin DSL multi-module build
- Spring Data JPA
- Flyway
- PostgreSQL
- Spotless + ktlint
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
    ├── pg-client
    ├── persistence
    └── test-support
```

`domains/*` are Gradle modules. API and scheduler modules depend on domain modules instead of owning business rules directly.

Multi-PG support starts in `libs/pg-client`. Payment code can resolve a PG client by `PgProvider`, while concrete PG implementations can be added later without turning PG itself into a business domain module.

## Local Run

```bash
mise install
docker compose up -d postgres
mise exec -- ./gradlew :apps:api:bootRun
```

Run the scheduler app separately:

```bash
mise exec -- ./gradlew :apps:scheduler:bootRun
```

Default local database settings are defined in:

- `apps/api/src/main/resources/application.yml`
- `apps/scheduler/src/main/resources/application.yml`

## Quality Checks

```bash
mise exec -- ./gradlew spotlessCheck check
mise exec -- ./gradlew spotlessApply
```

## Architecture Notes

See `docs/architecture/project-structure.md`.
