# search-platform

[![CI](https://github.com/PotatoPoweredClock/search-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/PotatoPoweredClock/search-platform/actions/workflows/ci.yml)

A package search platform in Java, built as a learning project. The goal is npm's public change feed into Postgres,
through Kafka into Elasticsearch, served by Spring Boot and a React UI, deployed locally to Kubernetes.

Only the first slice exists so far (milestone M0). See [CLAUDE.md](CLAUDE.md) for how this repo works and the current
milestone, and [docs/plan.md](docs/plan.md) for the full architecture and milestone plan.

## Quick start

Requires Java 25 and Docker. Everything uses the Maven wrapper.

**1. Start the infrastructure** (Postgres, Kafka, Elasticsearch):

```bash
docker compose up -d
docker compose ps    # wait until postgres and elasticsearch show "healthy"
```

**2. Run the ingest service:**

```bash
./mvnw -pl services/ingest-service spring-boot:run
```

On startup it creates the Postgres schema (Flyway) and the `npm-packages` Elasticsearch index, then starts paging npm's
change feed. It stops with Ctrl-C. The feed position is stored in Postgres, so a restart resumes where it left off.

**3. Watch data land:**

```bash
# Packages written to Postgres
docker compose exec postgres psql -U search_platform -d search_platform \
  -c "select count(*) from npm_package;" \
  -c "select source, cursor_value from cursor_storage;"

# Documents in Elasticsearch
curl -s 'localhost:9200/npm-packages/_count?pretty'

# Look at a few
curl -s 'localhost:9200/npm-packages/_search?size=3&pretty'

# Keyword search straight against Elasticsearch
curl -s 'localhost:9200/npm-packages/_search?q=react&pretty'

# Service health
curl -s localhost:8081/actuator/health
```

The Postgres count and the Elasticsearch count should climb together, and `cursor_value` should advance. The logs are
JSON (ECS format) and show each page being processed.

`search-api` (port 8080) starts but has no `/search` endpoint yet; that is the main open M0 task. Until then, query
Elasticsearch directly as above.

**Stopping:** `docker compose down` keeps the data volumes; add `-v` to wipe Postgres and Elasticsearch.

> Don't run the compose stack and a kind cluster at the same time: the target machine is a 24 GB laptop.
> Elasticsearch heap is 1 GB and Kafka heap is 512 MB.

### Configuration

Connection settings are in each service's `application.yml` and match the values in `compose.yaml`.
[`.env.example`](.env.example) documents the variables for local use. Nothing reads a `.env` file yet, and `.env`
is gitignored, so never commit one.

### Commands

- Build and test everything: `./mvnw verify`
- Unit tests only: `./mvnw test`
- Integration tests (`*IT`) need Docker running and only run in `verify`
- Run one service: `./mvnw -pl services/ingest-service spring-boot:run`
- Observability stack (when needed): `docker compose --profile obs up -d`

## Architecture

### Today (M0)

```
 npm _changes ──► ingest-service ──► Postgres (packages + cursor)
                       │
                       └──────────► Elasticsearch (npm-packages index)
```

`ingest-service` pages npm's `_changes` feed from a stored cursor, writes packages to Postgres, and syncs them
straight into Elasticsearch. There is no Kafka in the path yet; see
[ADR 0001](docs/adr/0001-direct-es-sync-from-ingest.md).

### Target

```
 npm _changes ──► ingest-service ──► Postgres (system of record + outbox)
 grants.gov ───►  (connectors,              │
                   virtual-thread            ▼ outbox relay
                   fetcher)               Kafka topics
                                             │
                        ┌────────────────────┼─────────────────────┐
                        ▼                    ▼                     ▼
                 indexer-service      enrichment-worker      (DLQ / retry)
                 bulk → Elasticsearch  embeddings, tags
                        │                    │
                        ▼                    ▼
                    Elasticsearch  (keyword + dense_vector, alias-swapped)
                        │
         search-api (Spring MVC, virtual threads)
                        │
         gateway (WebFlux): streaming answers (SSE), MCP server, advisor agent
                        │
                  React UI (Vite, TypeScript)
```

This is the diagram from [docs/plan.md](docs/plan.md). Everything past `ingest-service` and Elasticsearch is planned.

## What exists today (M0, in progress)

| Module                                               | State                                                                                                        |
|------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| [libs/core-model](libs/core-model)                   | Done for M0: sealed `ChangeEvent` types, `ConnectorCursor`, `NpmPackageDocument`.                            |
| [libs/connector-api](libs/connector-api)             | Done for M0: the `Connector` seam that data sources plug in behind.                                          |
| [libs/connector-npm](libs/connector-npm)             | Done for M0: npm `_changes` paging and package metadata fetch, with unit tests.                              |
| [services/ingest-service](services/ingest-service)   | Working: ingest loop with backoff, cursor persistence, Flyway schema, ES index creation and sync. Port 8081. |
| [services/search-api](services/search-api)           | Skeleton only: the Spring Boot app and config, no endpoint yet. Port 8080.                                   |
| [services/indexer-service](services/indexer-service) | Skeleton only: unused until Kafka arrives in M2.                                                             |

Other pieces in place:

- `compose.yaml` with Postgres 17, a single-broker Kafka (KRaft) and Elasticsearch 9. Kafka is running but nothing uses
  it yet. The `obs` profile adds Grafana `otel-lgtm`, also unused so far.
- Structured JSON logs (ECS format) in every service.
- Unit tests (`*Test`) and Testcontainers integration tests (`*IT`) for the repository and ES sync service.
- GitHub Actions: `ci.yml` (`./mvnw verify`), `codeql.yml`, `trufflehog.yml` (secret scanning), plus Dependabot.
- Design decisions recorded in [docs/adr](docs/adr). The decision sections are still being written.

### Still open for M0

Tracked in [docs/m0-remaining-checklist.md](docs/m0-remaining-checklist.md):

- `GET /search?q=` in `search-api` (query building, DTO, `ProblemDetail` errors, tests)
- one end-to-end round-trip `*IT`: canned feed, Postgres, Elasticsearch, search
- a live run against the real npm feed
- core-model unit tests, a green CI run, then making the repo public

## License

There is no license. This is a personal learning project, published to show the work, and isn't meant for reuse.
Without a license, all rights are reserved by default.

## Planned, not built yet

Each item below belongs to a later milestone in [docs/plan.md](docs/plan.md). None of it exists in the repo today.

| Milestone | What it adds                                                                                                            |
|-----------|-------------------------------------------------------------------------------------------------------------------------|
| M1        | Rate-limited concurrent metadata fetcher, idempotent writes, API hardening, Spring Security basics, metrics, dashboards |
| M2        | Transactional outbox and relay, Kafka topics, idempotent indexer consumer, DLQ and retry                                |
| M3        | Container images, a kind cluster via Terraform, Argo CD, one Helm chart, smoke tests                                    |
| M4        | Facets, autocomplete, alias-swap reindexing, relevance benchmark, React search UI, Cypress                              |
| M5        | Embeddings and hybrid keyword plus vector search                                                                        |
| M6        | Streaming gateway (WebFlux), MCP server, dependency-advisor agent, agent evals                                          |
| M7        | Delivery-loop agents: triage, regression filing, PR review, E2E analysis                                                |
| M8        | OAuth2 identity, BFF gateway, document-level security                                                                   |
| M9        | Idempotency keys, webhooks, caching, rate limiting, SLOs and runbooks                                                   |
| M10       | A second connector (grants.gov snapshots) behind the same interface                                                     |

Modules and directories in the plan that don't exist yet: `enrichment-worker`, `gateway`, `libs/connector-grants`,
`agents/`, `web/`, `e2e/`, `deploy/` and `infra/`.
