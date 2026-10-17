# 0005. Package detail reads come from Elasticsearch only

Status: accepted

## Context

`search-api` needs to return package details as well as search hits. Postgres is the system of record, but only
`ingest-service` talks to it today.

## Options

- **A. Elasticsearch only.** `search-api` has no datasource; the Elasticsearch doc carries everything the API returns.
- **B. Elasticsearch for search, Postgres for detail.** Fresh and complete, but couples the API to the ingest schema and
  adds a database dependency to every deploy.
- **C. Separate read model service.** Overkill for this scale.

## Decision

**Option A is the path chosen.** Elasticsearch will contain only the data from the package needed for searching and
returning important information.

As the data grows and the package in Postgres contains more data than Elasticsearch I will need to make an endpoint to
get the full package data from the database.

## Consequences

`search-api` will have limited information but the full package information can be retrieved from an additional API in a
different service that uses the database.

This does allow `search-api` to be its own service and can scale as independently as needed.
