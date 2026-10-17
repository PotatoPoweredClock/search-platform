# 0006. HTTP client used by connectors

Status: accepted

## Context

The npm connector calls the registry's `_changes` feed and, per ADR 0003, possibly package metadata. It already uses
Spring's `RestClient` types (`RestClientException`, `HttpClientErrorException` in `NpmPackageConnector`). The connector
libs are meant to be small and replaceable behind `Connector`.

## Options

- **A. JDK `HttpClient`.** No Spring dependency, but no Spring observation or metrics integration and more hand-rolled
  error handling.
- **B. Spring `RestClient`.** Synchronous, fits virtual threads, observations and metrics come via the injected builder,
  `MockRestServiceServer` for tests.
- **C. `WebClient`.** Reactive; unneeded since the stack is blocking on virtual threads.

## Decision

Option B won out after attempting Option A.

I first attempted Option A and decided it was not worth the effort. Using the `RestClient` simplified the
implementation and testing enough to justify using it over trying to implement code at a lower level.

## Consequences

Not as much lower-level control but I also don't have to deal with the lower-level pain points.
