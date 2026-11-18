# Deployment

The Compose stack defines PostgreSQL/PostGIS, Redis, Kafka, Mailpit, a demo OAuth2 issuer, and the application. Services bind to loopback for local development. `.env.example` contains placeholders; supply local values through an untracked `.env` file. Never use the demo issuer or development credentials in production.

```sh
docker compose --env-file .env up --build
```

The Dockerfile builds/runs on Java 17. PostgreSQL, Redis, Kafka and Mailpit use Compose health/start conditions. Kafka is a single-node development configuration. Production needs managed/secured infrastructure, a real identity provider, TLS, managed secrets, backup/restore, monitoring and capacity planning. No production deployment is configured by this repository.

Integration tests use Docker/Testcontainers to apply migrations to PostgreSQL/PostGIS. A full production deployment smoke test is not provided. Check [security](SECURITY.md) and [database schema](DATABASE_SCHEMA.md) before deployment.
