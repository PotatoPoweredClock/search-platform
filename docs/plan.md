# Plan

A search platform for npm packages, built to exercise modern Java and Spring Boot, Kafka, Elasticsearch, Kubernetes and
applied AI end to end. Each data source is its own client and ingest pipeline, and every source emits the same change
events: npm first (a sequence-cursor change feed), grants.gov second (daily snapshots diffed into changes).

## Architecture

```
 npm _changes ──► npm source ──► RabbitMQ ──► npm workers ──┐
                 (poller, cursor)  (fetch tasks)  (rate-limited client)
 grants.gov ───► grants source (snapshot diff) ────────────┤ change events
                                                           ▼
                                  ingest-service (change sink) ──► Postgres (system of record + outbox)
                                                                        │
                                                                        ▼ outbox relay
                                                                     Kafka topics
                                                                        │
                        ┌───────────────────────────────────────────────┼─────────────────────┐
                        ▼                                               ▼                     ▼
                 indexer-service                                 enrichment-worker      (DLQ / retry)
                 bulk → Elasticsearch                            embeddings, tags
                        │                                               │
                        ▼                                               ▼
                    Elasticsearch  (keyword + dense_vector, alias-swapped)
                        │
         search-api (REST + GraphQL, virtual threads)
                        │
         gateway (WebFlux): streaming answers (SSE), MCP server, advisor agent
                        │
                  React UI (Vite, TypeScript)
```

REST and GraphQL read APIs are kept at parity by a test that runs the same queries through both; writes stay REST-only.

Why both MVC and WebFlux: blocking code on virtual threads where the work is request and response; reactive where the
work is genuinely streaming, with backpressure end to end.

**Sources and the shared contract.** There is no shared source interface. Paging, per-document fetching, rate limiting
and queues differ per source, so each source keeps them private. What sources share is downstream: a change event
(source, entity id, a version string that is comparable per entity, observed time, and an upsert payload or a delete) and a
change sink that `ingest-service` implements (one Postgres transaction per batch, guarded on the version, plus an outbox
row). Delivery is at-least-once, so the sink must be idempotent.

**Two kinds of messages.** RabbitMQ carries fetch tasks (commands, where per-message ack, delayed retry and dead-lettering
matter). Kafka carries change events (facts, read by several consumers). The npm poller advances its cursor only after
the publisher confirms the whole page, so a crash replays the page and duplicates are absorbed by the idempotent writes.
RabbitMQ and Postgres cannot commit together; ordering plus idempotency replaces atomicity.

## Layout

```
pom.xml, mvnw
libs/        core-model, http-client, source-npm, source-grants (the current connector-* modules are being reworked)
services/    ingest-service, indexer-service, enrichment-worker, search-api, gateway
agents/      agent-core, triage-agent, regression-agent, review-agent, e2e-analysis-agent
web/         React UI
e2e/         Cypress
deploy/      charts/spring-service, envs/e2e, argocd
infra/       terraform (kind cluster, Argo CD bootstrap)
compose.yaml
docs/        plan.md, adr/, ai-log.md, runbooks/
```

## Milestones

- **M0, stand it up.** Maven multi-module skeleton, compose, CI. Core model; the npm connector paging `_changes` from a
  stored cursor into Postgres (Flyway). Direct indexing into Elasticsearch, one search endpoint, structured JSON
  logs. Repo public.
- **M1a, ingest.** The npm source replaces the single-process ingest loop. A scheduled poller pages `_changes` from a
  stored cursor and publishes one fetch task per package to RabbitMQ (persistent messages, publisher confirms, the cursor
  advanced only after the whole page is confirmed, polling paused while the queue is deep). Workers (Spring AMQP) fetch
  each package document through a rate-limited HTTP client (a shared token bucket, a per-host circuit breaker,
  timeouts), with delayed retry and a dead-letter queue for failed fetches. A change sink in `ingest-service` writes
  Postgres and then a single-document write to Elasticsearch; bulk writes return with Kafka in M2. Measured with JMH and
  a contention test on the limiter and breaker under concurrent workers; Testcontainers for RabbitMQ; the end-to-end
  round-trip test (canned feed through Postgres and Elasticsearch to the search endpoint), carried over from M0. Metrics for queue
  depth, unacked messages, DLQ size and confirm latency. Test-first core.
    - Idempotent package writes in Postgres and Elasticsearch: duplicates, replays and out-of-order tasks must not
      overwrite a newer package with an older one (guard the Postgres upsert on a comparable version, and use
      Elasticsearch external versioning with the same value).
    - ADRs: why a work queue for fetch tasks (the tradeoffs, and what the alternative would be), and publisher confirms
      plus replay instead of a second outbox for the cursor.
- **M1b, API and security.** API hardening: pagination, ProblemDetail, health. Spring Security basics: filter chain,
  API-key filter on admin endpoints, method security, CORS, a test that no admin endpoint answers unauthenticated. A
  read-only GraphQL API next to REST (search plus a package query with nested dependencies, maintainers and versions),
  with the N+1 problem fixed by `@BatchMapping` and measured, depth and complexity limits, and a REST/GraphQL parity
  test. Metrics (RED and USE) for the API, `otel-lgtm` in compose, HTTP tracing, first dashboard. ArchUnit rules for the
  source seam (sources depend on the core model and the HTTP client, not on persistence or Elasticsearch; only
  `ingest-service` implements the sink).
- **M2, Kafka.** Transactional outbox and relay with `FOR UPDATE SKIP LOCKED`: the sink writes Postgres and the outbox
  row in one transaction, and the direct Elasticsearch write is replaced by an idempotent indexer consumer. Bulk writes
  return here: chunk by count or bytes (or use `BulkIngester`), classify Elasticsearch write failures as retryable (e.g. 429) vs
  permanent (e.g. 400 mapping errors) with an exception that carries the failed ids and statuses, retry only the failed
  ids, and send permanent failures to the DLQ; consider external versioning so out-of-order events cannot
  overwrite newer docs. Kill the consumer mid-run and show the index converges. Consumer lag, outbox age and index
  freshness metrics (freshness includes queue time); trace context through the outbox, Kafka and RabbitMQ headers. A
  query-plan fix in Postgres written up as an ADR.
- **M3, local platform.** Jib images to GHCR, a kind cluster built by Terraform, Argo CD syncing an `e2e` namespace, one
  Helm chart for all services, PostSync smoke tests, CI tag bumps, SBOM and image scanning. Zero-downtime deploy demo:
  rolling update under load plus an expand-and-contract migration. Cluster logs, metrics and traces in Grafana.
- **M4, search quality and UI.** Facets, autocomplete, alias-swap reindex, a twenty-query relevance benchmark. React
  search UI (debounce, AbortController, honest loading, empty and error states). Cypress in the PostSync hook.
- **M5, semantic search.** Enrichment worker embedding locally (Ollama) into `dense_vector`; hybrid BM25 plus kNN with
  reciprocal rank fusion; LLM-drafted relevance labels, spot-checked; benchmark before and after.
- **M6, AI layer.** WebFlux gateway streaming answers with citations; MCP server exposing search tools;
  dependency-advisor agent with tool allow-list and step budget; evals in CI; OpenTelemetry traces for every agent step
  and tool call. Optional: structured concurrency (`StructuredTaskScope`, a preview API in Java 25) for the advisor
  agent's per-dependency fan-out, with a timeout per branch and cancellation, compared against a plain virtual-thread
  `ExecutorService` in JMH.
- **M7, delivery-loop agents.** Triage (diagnosis plus calibrated confidence), regression filing, PR review with
  command-gated commits, E2E analysis over Loki, Prometheus and Tempo. Least-privilege credentials, untrusted-input
  handling, budgets, traces; planted bugs as the triage eval set.
- **M8, security and identity.** FusionAuth locally; search-api as an OAuth2 resource server; gateway as a BFF
  (Authorization Code with PKCE, tokens server-side); client credentials between services; private collections with
  document-level security in Elasticsearch, measured at 1, 50 and 500 groups, enforced on GraphQL too and proved by the
  parity test; narrow-scoped tokens for MCP and agents.
- **M9, API hardening.** Idempotency keys; HMAC-signed webhooks with retries, a delivery log, a per-subscriber
  concurrency cap and a circuit breaker that pauses delivery to a failing endpoint; Redis cache with stampede guard; the
  token bucket from the HTTP client moved behind the same interface to Redis with an atomic Lua script, and a rate
  limiter per principal built on it (ADR on what changes with several instances and clock skew); cursor pagination (REST
  and GraphQL); ETag plus `If-Match` conditional writes on shared collections (412 on a stale version). SLOs with
  burn-rate alerts and runbooks, an observed incident drill and postmortem, a JFR profiling exercise. `API.md`.
- **M10, second source.** grants.gov as its own client and source (snapshot diffing) emitting the same change events;
  ADR comparing the two freshness models.

## Threads that run through every milestone

- **Concurrency:** ingest workers and the shared rate limiter, outbox relay, bulk indexer, Kafka consumers, webhook
  delivery caps, cache stampede guard.
- **Observability:** JSON logs with trace IDs from M0; metrics from M1a; async tracing from M2; SLOs and incidents in M9.
  Index freshness is the key SLI.
- **Security:** basics in M1b, identity in M8, agent scoping in M7.
- **API parity:** from M1b, every REST read endpoint has a GraphQL field, checked by the parity test.
- **Docs:** an ADR per real decision, `docs/ai-log.md`, runbooks.

## Memory budget (24 GB)

Dev mode (compose) and e2e mode (kind) never run together.

| Mode                                                                                | Rough RAM     |
|-------------------------------------------------------------------------------------|---------------|
| Dev: Postgres, Kafka, Elasticsearch, services from the IDE, `otel-lgtm` when needed | 4.5 to 5.5 GB |
| E2E: kind, trimmed Argo CD, infra, services, `otel-lgtm`; FusionAuth from M8        | 7 to 9 GB     |
| Ollama (native) for embeddings                                                      | about 0.5 GB  |

RabbitMQ adds one container in dev and in e2e; its footprint has not been measured yet.

Use official images (`apache/kafka`, Elastic's, `postgres`, `rabbitmq`).
