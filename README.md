# search-platform

A package search platform: npm's public change feed into Postgres, through Kafka into Elasticsearch, served by Spring Boot and a React UI, deployed locally to Kubernetes.

See [CLAUDE.md](CLAUDE.md) for how this repo works and the current milestone, and [docs/plan.md](docs/plan.md) for the architecture and milestone plan.

## Commands

- Build and test everything: `./mvnw verify`
- Unit tests only: `./mvnw test`
- Run one service: `./mvnw -pl services/search-api spring-boot:run`
- Local infrastructure: `docker compose up -d`
- Observability stack (when needed): `docker compose --profile obs up -d`
