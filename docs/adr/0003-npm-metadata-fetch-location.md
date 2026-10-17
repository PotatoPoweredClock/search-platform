# 0003. Where the npm metadata fetch lives

Status: accepted

## Context

The `_changes` feed returns package ids and sequence numbers, not full metadata. Full metadata (description, keywords,
latest version) needs a per-package request to the registry. M1 adds a rate-limited concurrent fetcher on virtual
threads. In M0 the fetch has to live somewhere that will not need to be torn apart in M1.

## Options

- **A. Inside the connector** (`Connector.getChangePage` returns enriched events). Callers stay simple; the connector
  owns registry knowledge and the page call becomes slow and partially failing.
- **B. A separate fetcher in ingest**, driven by the feed's ids. Keeps the connector a thin feed reader and is where
  M1's concurrency work naturally lands.
- **C. Lazy enrichment** in a later pipeline stage. Defers cost but leaves incomplete docs in the index.

## Decision

For M0 I will do everything in the connector and write the code in a way that makes it easy to refactor for changes to
be made in M1.

Everything comes back in one response for simplicity but it doesn't scale well and is not how I want to implement this
in the first place.

I allowed Claude to plan this project to see what decisions it would make and how they compare to my own. I will make
changes to the plan as I see fit.

## Consequences

Retryable errors like 429 and 5xx cause the entire page to fail and retry.

A failure like 404 would mean the package doesn't exist so it will be marked as a delete to make sure it is updated
in Postgres and removed from Elasticsearch.

The connector isn't going to stay source agnostic.
