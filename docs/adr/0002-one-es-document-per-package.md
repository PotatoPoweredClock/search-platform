# 0002. Index one document per package, from the latest version

Status: accepted

## Context

npm packages have many versions. The search UI and API return packages, and the `_changes` feed signals changes per
package, not per version. The index design determines mapping, document count (cap of a few thousand in M0, ~100K later)
and what a search hit means.

## Options

- **A. One document per package**, built from the latest version's metadata. Small index, hits are packages.
- **B. One document per version.** Enables version-specific search but multiplies the doc count and needs collapsing at
  query time.
- **C. Package document with a nested versions array.** Heavy for packages with thousands of versions.

## Decision

For now we will go with the simple option of only keeping the latest released package version. We will likely get to the
point where we want to store information about multiple versions of a package and will have to adjust the Postgres and
Elasticsearch schema as well as the queries. This will help us get M0 done faster and help keep memory and storage usage
down to help with the hardware limitations that I have.

## Consequences

Depending on later features I will need to restructure the Postgres schema and limit what gets sent to Elasticsearch. I
need to keep in mind the costs of refactoring, schema changes, and reindexing.

Latest is determined by info from the `dist-tags` so we now rely on that being updated correctly in the npm registry.
