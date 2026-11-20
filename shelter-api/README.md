# shelter-api

The REST API of the Shelter application, written in Spring Boot (Java 21) on PostgreSQL.

## What it does

- Registration, login, refresh and logout under `/api/auth`, with short-lived access tokens and rotating refresh tokens.
- Dogs, their care data and images under `/api/dogs`, and sponsorship requests under `/api/sponsorships`.
- `/api/health` and `/api/info` (the revision the running instance was built from), which the pipeline uses to check a deployment.
- The database schema is managed by Flyway and migrated when the application starts.

## Configuration

Required, no defaults: `JWT_SECRET`, `ALLOWED_ORIGINS`, `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` and `SUPABASE_BUCKET`.
The database connection uses the standard `SPRING_DATASOURCE_*` variables. The first administrator is created from
`ADMIN_EMAIL` and `ADMIN_PASSWORD` when they are set. Values are never kept in the repository.

## Build and test

```
./mvnw clean compile
./mvnw test                                                    # unit tests
./mvnw verify -DskipUTs=true -Dit.test=*IntegrationTest        # needs Docker (Testcontainers)
./mvnw verify -DskipUTs=true -Dit.test=*AcceptanceTest
./mvnw jacoco:check@coverage-check                             # at least 85 % line coverage
```

The same commands run in `Jenkinsfile.ci`.
