# 0004. Shape of `ConnectorCursor` and how paging and the subset cap work

Status: accepted

## Context

`ConnectorCursor` is currently a record wrapping a single string. npm's feed uses a numeric sequence, grants.gov (M10)
uses snapshot diffing, so the cursor must stay opaque to the ingest loop. M0 must also stop after a few thousand
packages (constraint: index a subset).

## Options

- **A. Opaque string cursor** (current), connector parses it. Source-agnostic, weakly typed.
- **B. Typed cursor per connector**, generic `Connector<C>`. Type-safe, spreads generics through ingest and persistence.
- **C. Structured cursor** (type tag plus JSON payload). Flexible, more to validate.

Subset cap: **(i)** stop after N packages stored, **(ii)** stop at a target sequence number, **(iii)** a config flag
that pauses polling.

## Decision

I chose a string cursor for simplicity in usage and storage in Postgres.

I am not going to implement soft cap directly. The default cursor value kind of does that by forcing a starting point.

## Consequences

I need to be careful modifying and using the cursor as the text has to be parsed properly though it is easy enough to
make a util to do so.

The soft cap is not an issue because of the starting point. As time goes on the starting point gets farther behind the
current time and takes longer to catch up.

That is fine for this project because it is a learning project.
