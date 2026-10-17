# 0001. Ingest syncs directly to Elasticsearch in M0

Status: accepted

## Context

M0 needs a search query to work end to end, but Kafka and the outbox arrive in M2. Something has to move data from
Postgres into Elasticsearch in the meantime. Elasticsearch documents live in `core-model` so every module can share the
shape. Ingest currently stores packages in Postgres and advances a cursor; it has no Elasticsearch code.

## Options

- **A. Ingest writes to Elasticsearch right after the Postgres commit** (`IndexWriter` interface, bulk implementation,
  package name as `_id`). Simplest; cursor advances only after both succeed.
- **B. `indexer-service` reads from Postgres** on a schedule (e.g. by `updated_at` watermark). Matches the final service
  split, but needs a second watermark and a polling loop that M2 throws away.
- **C. Wait for Kafka** and do M2 first. Violates M0's done-when.

## Decision

**Option A is the path chosen.** With a full service `NpmElasticSearchSyncService` instead of an `IndexWriter`. The
M0 implementation will end up being mostly throwaway work as far as the ingest process goes and possibly the connector
too. This gets us something that works quicker and the service code can be reused easily later and with multiple
different processes.

## Consequences

The ingest process will either completely pass or fail and a failure can cause unnecessary work in calling the npm
package API and persisting the results to Postgres and Elasticsearch, which is fine for now but something we want to
look at and improve soon.

Updates are idempotent because the process always fetches the most recent version of the package document.

If Elasticsearch sync fails the database will be ahead until the process reruns and succeeds at the sync loop but that
time should be short.
