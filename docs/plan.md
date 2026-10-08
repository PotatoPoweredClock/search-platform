# Plan

A search platform for npm packages, built to exercise modern Java and Spring Boot, Kafka, Elasticsearch, Kubernetes and
applied AI end to end. Data sources plug in behind one interface: npm first (a sequence-cursor change feed), grants.gov
second (daily snapshots diffed into changes).

## Architecture

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

Why both MVC and WebFlux: blocking code on virtual threads where the work is request and response; reactive where the
work is genuinely streaming, with backpressure end to end.

## Layout

```
pom.xml, mvnw
libs/        core-model, connector-api, connector-npm, connector-grants
services/    ingest-service, indexer-service, enrichment-worker, search-api, gateway
agents/      agent-core, triage-agent, regression-agent, review-agent, e2e-analysis-agent
web/         React UI
e2e/         Cypress
deploy/      charts/spring-service, envs/e2e, argocd
infra/       terraform (kind cluster, Argo CD bootstrap)
compose.yaml
docs/        plan.md, adr/, runbooks/
```

## Milestones

- **M0, stand it up.** Maven multi-module skeleton, compose, CI. Core model and connector interface; the npm connector
  paging `_changes` from a stored cursor into Postgres (Flyway). Direct indexing into Elasticsearch, one search
  endpoint, one Testcontainers round-trip test, structured JSON logs. Repo public.
- **M1, Java depth.** Rate-limited metadata fetcher on virtual threads (semaphore, bounded queue, jittered retries),
  measured with JMH and a contention test. Optional: structured concurrency (`StructuredTaskScope`, a preview API in
  Java 25) for the per-page fan-out, with a subtask returning `Optional` for a 404 package so only other failures fail
  the page; compare against a plain virtual-thread `ExecutorService` in JMH. API hardening: pagination, ProblemDetail,
  health. Spring Security basics: filter chain, API-key filter on admin endpoints, method security, CORS, a test that no
  admin endpoint answers unauthenticated. Metrics (RED and USE), `otel-lgtm` in compose, first dashboard. ArchUnit rules
  for the connector seam. Test-first core.
    - Idempotent package writes in Postgres and Elasticsearch: re-processing or out-of-order updates must not overwrite
      a newer package with an older one (e.g. guard the Postgres upsert on the feed sequence or modified date, and use
      Elasticsearch external versioning with the same value).
    - Carried over from M0 review of `NpmElasticSearchSyncService`: classify bulk failures as retryable (e.g. 429) vs
      permanent (e.g. 400 mapping errors), and have the sync exception carry failed ids and statuses.
- **M2, Kafka.** Transactional outbox and relay with `FOR UPDATE SKIP LOCKED`, idempotent indexer consumer, DLQ and
  retry. Kill the consumer mid-run and show the index converges. Consumer lag, outbox age and index freshness metrics;
  trace context through the outbox and Kafka headers. A query-plan fix in Postgres written up as an ADR.
    - Carried over from M0 review of `NpmElasticSearchSyncService`: the consumer uses the M1 failure classification to
      retry or send to the DLQ, and retries only failed ids; chunk bulk requests by count or bytes (or use
      `BulkIngester`) instead of one request per batch. Also consider external versioning so out-of-order events cannot
      overwrite newer docs.
- **M3, local platform.** Jib images to GHCR, a kind cluster built by Terraform, Argo CD syncing an `e2e` namespace, one
  Helm chart for all services, PostSync smoke tests, CI tag bumps, SBOM and image scanning. Zero-downtime deploy demo:
  rolling update under load plus an expand-and-contract migration. Cluster logs, metrics and traces in Grafana.
- **M4, search quality and UI.** Facets, autocomplete, alias-swap reindex, a twenty-query relevance benchmark. React
  search UI (debounce, AbortController, honest loading, empty and error states). Cypress in the PostSync hook.
- **M5, semantic search.** Enrichment worker embedding locally (Ollama) into `dense_vector`; hybrid BM25 plus kNN with
  reciprocal rank fusion; LLM-drafted relevance labels, spot-checked; benchmark before and after.
- **M6, AI layer.** WebFlux gateway streaming answers with citations; MCP server exposing search tools;
  dependency-advisor agent with tool allow-list and step budget; evals in CI; OpenTelemetry traces for every agent step
  and tool call.
- **M7, delivery-loop agents.** Triage (diagnosis plus calibrated confidence), regression filing, PR review with
  command-gated commits, E2E analysis over Loki, Prometheus and Tempo. Least-privilege credentials, untrusted-input
  handling, budgets, traces; planted bugs as the triage eval set.
- **M8, security and identity.** FusionAuth locally; search-api as an OAuth2 resource server; gateway as a BFF
  (Authorization Code with PKCE, tokens server-side); client credentials between services; private collections with
  document-level security in Elasticsearch, measured at 1, 50 and 500 groups; narrow-scoped tokens for MCP and agents.
- **M9, API hardening.** Idempotency keys; HMAC-signed webhooks with retries and a delivery log; Redis cache with
  stampede guard; rate limiter per principal; cursor pagination; ETag. SLOs with burn-rate alerts and runbooks, an
  observed incident drill and postmortem, a JFR profiling exercise. `API.md`.
- **M10, second connector.** grants.gov snapshot diffing; ADR comparing the two freshness models.

## Threads that run through every milestone

- **Concurrency:** fetcher, outbox relay, bulk indexer, Kafka consumers, gateway fan-out, cache stampede guard.
- **Observability:** JSON logs with trace IDs from M0; metrics from M1; async tracing from M2; SLOs and incidents in M9.
  Index freshness is the key SLI.
- **Security:** basics in M1, identity in M8, agent scoping in M7.
- **Docs:** an ADR per real decision, runbooks.

## Memory budget (24 GB)

Dev mode (compose) and e2e mode (kind) never run together.

| Mode                                                                                | Rough RAM     |
|-------------------------------------------------------------------------------------|---------------|
| Dev: Postgres, Kafka, Elasticsearch, services from the IDE, `otel-lgtm` when needed | 4.5 to 5.5 GB |
| E2E: kind, trimmed Argo CD, infra, services, `otel-lgtm`; FusionAuth from M8        | 7 to 9 GB     |
| Ollama (native) for embeddings                                                      | about 0.5 GB  |

Use official images (`apache/kafka`, Elastic's, `postgres`).
