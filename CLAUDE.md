# CLAUDE.md

A package search platform in Java: npm's public change feed into Postgres, through Kafka into Elasticsearch, served by
Spring Boot services and a React UI, deployed locally to Kubernetes. Data sources plug in behind a `SourceConnector`
interface. The full plan is in `docs/plan.md`.

## How we work (read this first)

This is a learning project. I write the core backend by hand; your main job there is to review, explain and answer
questions.

**Hand-written by me. Don't write or rewrite code here unless I explicitly ask:**

- `libs/core-model`, `libs/connector-api`, `libs/connector-*`
- the concurrent fetcher, outbox relay, Kafka consumers, bulk indexer, reindexing, query building
- Spring Security configuration, filters and JWT mapping
- custom metrics, spans and trace propagation
- the RAG pipeline, `agents/agent-core`, agent tool definitions
- the tests for all of the above

In those areas: review with specific file and line references, explain the why, point to the relevant docs or JDK/Spring
source, and suggest the smallest change. Hints before answers when I'm stuck, unless I ask for the answer.

**You may scaffold and build (I review every line):**
Maven POMs and module skeletons, `compose.yaml`, GitHub Actions, Helm, Argo CD, Terraform, the React UI, Cypress tests,
Grafana dashboards, config boilerplate. Keep it minimal, explain what you generated and why, and flag anything you're
unsure of.

**Habits:**

- Real decisions get an ADR in `docs/adr/NNNN-title.md`: context, options, decision, consequences. You can draft the
  skeleton and options; I write the decision.
- Once per milestone, when I ask, plant a bug on a branch named `drill/<milestone>` without telling me where. Put the
  answer in `.drills/` (gitignored). Later the drills become the triage agent's eval set.

## Stack

Java 25, Spring Boot 4.1, Maven (use `./mvnw`), PostgreSQL with Flyway, Kafka (KRaft, single broker), Elasticsearch,
Spring AI 2.0 (from M5), React with TypeScript and Vite, Cypress, kind, Helm, Argo CD, Terraform, Grafana `otel-lgtm`
(Prometheus, Loki, Tempo).

Java throughout for backend code. No Kotlin modules.

## Commands

- Build and test everything: `./mvnw verify`
- Unit tests only: `./mvnw test`
- Integration tests (`*IT`) need Docker running and only run in `verify`, never in `test`. One IT:
  `./mvnw -pl services/ingest-service verify -Dit.test=NpmElasticSearchSyncServiceIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- Run one service: `./mvnw -pl services/search-api spring-boot:run`
- Local infrastructure: `docker compose up -d`
- Observability stack (when needed): `docker compose --profile obs up -d`

(Update this list as modules and profiles appear.)

## Boot 4 and Jackson 3

Spring Boot 4 moved auto-configurations into new packages (for example
`org.springframework.boot.elasticsearch.autoconfigure`, `org.springframework.boot.jackson.autoconfigure`) and Jackson 3
lives under `tools.jackson`. Don't write Boot 3 era imports. Check the jar before importing an auto-configuration, and
import the ones it depends on too (the ES client needs `ElasticsearchRestClientAutoConfiguration`).

## Constraints

- **24 GB MacBook Air.** Never run the compose stack and the kind cluster at the same time. Elasticsearch heap 1 GB,
  Kafka heap 512 MB.
- **Index a subset of npm:** a few thousand packages in M0, up to roughly 100K later.
- **Local only, no cloud spend.**
- **Public repo:** never commit secrets, tokens or `.env` files. Use `.env.example` for documented variables.

## Conventions

- Structured JSON logs (Spring Boot structured logging). Never log tokens, API keys or `Authorization` headers.
- Unit tests are `*Test` (Surefire); integration tests are `*IT` (Failsafe) and use Testcontainers against real
  Postgres, Kafka and Elasticsearch.
- Outbox and cursor persistence use `JdbcClient` with hand-written SQL.
- Records for DTOs and events; a sealed `ChangeEvent` hierarchy.
- Error responses use `ProblemDetail`.

## Current milestone

**M0: stand it up.** Done when one search query works end to end (npm feed, Postgres, Elasticsearch, search endpoint),
one Testcontainers round-trip test passes, CI is green, and the repo is public. Update this section when a milestone
closes.
