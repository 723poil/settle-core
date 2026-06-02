# Project Structure

`settle-core` uses a Gradle multi-module layout. Runtime concerns are separated from business modules: API and scheduler live under `apps`, domain modules live under `domains`, and shared utilities or technical foundations live under `libs`.

Java and Kotlin tool versions are pinned in `.mise.toml`. Use `mise install` before running Gradle locally.

## Module Layout

```text
settle-core
├── apps
│   ├── api
│   └── scheduler
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

## Runtime Apps

- `:apps:api`: Spring Boot REST API server. It owns HTTP controllers, request/response DTOs, API exception handling, and API-specific configuration.
- `:apps:scheduler`: Spring Boot scheduler process. It owns scheduled jobs, batch triggers, and scheduler-specific configuration.

Both apps depend on domain modules and shared libs. Neither app should contain core payment or settlement rules.

## Domain Modules

- `:domains:merchant`: merchant registration, status, settlement recipient data, and merchant-facing policies.
- `:domains:payment`: payment, cancel, and partial-cancel transaction rules.
- `:domains:settlement`: settlement batches, settlement lines, fee/tax/net amount calculation, and payout readiness.

Domain modules are Gradle modules, not just folders. They should expose explicit Kotlin APIs to apps and other allowed modules. Do not pre-create `presentation`, `application`, or `infrastructure` packages inside every domain; add packages only when code needs them.

## Shared Libraries

- `:libs:common`: pure common types and utilities, such as money values and domain event contracts.
- `:libs:infra`: runtime infrastructure wiring shared by apps, such as Redis/Kafka starters and startup connection smoke checks.
- `:libs:pg-client`: PG provider identifiers, PG client contracts, and provider-based client lookup. This is for multi-PG integration plumbing, not PG business rules.
- `:libs:persistence`: JPA/Flyway/PostgreSQL support, shared persistence base classes, and database migrations.
- `:libs:test-support`: shared test fixtures and Testcontainers dependencies.

Keep business rules out of `libs`. If code talks about payment state, settlement calculation, or merchant policy, it belongs in a domain module. If code only selects or calls an external PG provider, it belongs in `:libs:pg-client`.

## Dependency Direction

```text
:apps:api
:apps:scheduler
    -> :domains:*
    -> :libs:infra
    -> :libs:persistence

:domains:payment
    -> :libs:pg-client

:domains:*
    -> :libs:common

:libs:pg-client
    -> :libs:common

:libs:persistence
    -> :libs:common
```

Cross-domain dependencies should stay explicit in Gradle. Keep empty future modules out of the build until their workflow and rules are clear.

## Persistence

Flyway migrations are owned by `:libs:persistence` under `libs/persistence/src/main/resources/db/migration`. Runtime apps load these migrations through their classpath. Hibernate is configured with `ddl-auto=validate`, so JPA mappings must follow migrations rather than generating production schema.

The initial schema supports:

- multiple PG providers
- multiple PG merchant accounts per merchant
- payment, cancel, and partial-cancel transaction records
- raw payment event history
- daily settlement batches per PG provider
- settlement lines linked back to payment transactions
