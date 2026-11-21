# Gym CRM

Trainees and trainers register, log in, manage their profiles, pick trainers and log trainings, all over HTTP. Data is stored in PostgreSQL. Spring Boot application with four environment profiles plus a standalone one for Docker, Flyway migrations, actuator health and Prometheus metrics, Swagger UI, and Spring Security with JWT bearer tokens. Since the Microservices module it is one of three services. Since the Messaging module it sends trainer workload changes to the second service through an ActiveMQ Artemis queue and reads the totals back over REST, finding that service through Eureka. Since the NoSQL module that service keeps the totals in MongoDB.

## Architecture

| Service | Folder | Purpose | Port | Base package |
|---|---|---|---|---|
| gym-crm | `/` (root) | Trainees, trainers, trainings, authentication; owns the PostgreSQL data | 8080 | `com.giorgi.gymcrm` |
| workload-service | `workload-service/` | Monthly training minutes per trainer, stored in MongoDB | 8081 | `com.giorgi.workload` |
| discovery-server | `discovery-server/` | Eureka registry | 8761 | `com.giorgi.discovery` |

```
client --JWT--> gym-crm :8080 --resolves "workload-service"--> Eureka :8761
                    |   |                                          |
                    |   | reads: REST, service JWT (workload:read) | instance list
                    |   +-----------------------------------> workload-service :8081
                    |                                                  ^
                    | writes: JMS, service JWT (workload:write)        | consumes      MongoDB :27017
                    +------------> Artemis :61616 [trainer.workload] --+               ^
                                                         workload-service ------------+
                                                         one document per trainer
```

Workload writes go through the broker; see Messaging. gym-crm never has a workload-service address
configured. For reads it calls `http://workload-service`, and the
load balancer replaces the name with an instance Eureka knows about. Both clients register with
Eureka and fetch the registry from it.

**Repo layout.** One repository with sibling folders and no parent pom. Each service has its own pom,
Dockerfile and image, so it builds and tests on its own (`./mvnw -f workload-service/pom.xml verify`),
while one `compose.yml` runs the whole environment and one owner keeps the three in step. Services share
no code: a class two services need is copied and adapted, so no change to one can break the build of
another. A parent pom or shared module would couple the builds this module is trying to keep apart.

## Stack

Java 21 and Spring Boot 4.0.8, Spring Cloud 2025.1.3 for Eureka, the load balancer and the circuit breaker, ActiveMQ Artemis (client 2.43.0, broker image 2.44.0) through `spring-boot-starter-artemis`, MongoDB 8.0 LTS (image `mongo:8.0.38`) through Spring Data MongoDB in workload-service. The versions Boot manages, from `./mvnw dependency:tree`:

| Part | Version |
|---|---|
| Spring Framework | 7.0.9 |
| Hibernate ORM | 7.2.24.Final |
| Jackson (`tools.jackson`) | 3.1.5 |
| Tomcat (embedded) | 11.0.26 |
| Flyway | 11.14.1 |
| Micrometer | 1.16.7 |
| Spring Security | 7.0.7 |
| jjwt (not managed by Boot) | 0.13.0 |
| springdoc OpenAPI | 3.0.3 |
| HikariCP | 7.0.2 |
| PostgreSQL driver | 42.7.13 |
| H2 (tests only) | 2.4.240 |
| Spring Data MongoDB | 5.0.7 |
| MongoDB Java driver | 5.6.5 |
| Testcontainers (workload-service tests) | 2.0.5 |

Plus Hibernate Validator, Lombok, JUnit 5, Mockito and JaCoCo.

## Getting it running

You need Java 21 and a PostgreSQL server on localhost:5432. Create the database first, Flyway creates the tables but not the database:

```sql
CREATE DATABASE gymcrm;
```

For the dev profile, create its database too:

```sql
CREATE DATABASE gymcrm_dev;
```

Credentials come from the environment, there are no defaults:

```powershell
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="yourpassword"
```

That is PowerShell. On macOS and Linux:

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword
```

The JWT signing secret is optional on local and dev, they fall back to a development secret. To use your own, set `JWT_SECRET` to a random value of at least 32 bytes:

```powershell
$env:JWT_SECRET="a-random-value-of-at-least-32-bytes"
```

Then start it:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The server starts on `http://localhost:8080`. The local profile is also the default, so plain `./mvnw spring-boot:run` lands on it.

In IntelliJ, make a Spring Boot run configuration and put `local` in the Active profiles field. If you only have a plain application configuration, add `-Dspring.profiles.active=local` to VM options instead. Set `DB_USERNAME` and `DB_PASSWORD` in the Environment variables field of the same dialog.

Tests run against in-memory H2, so they work on a machine with no database installed:

```bash
./mvnw clean verify
```

That also writes the coverage report to `target/site/jacoco/index.html`.

## Profiles

`application.yml` holds what every profile shares. Each profile adds its own database and its own levels.

| | local | dev | stg | prod |
|---|---|---|---|---|
| Database URL | `jdbc:postgresql://localhost:5432/gymcrm` | `DB_URL` or `localhost:5432/gymcrm_dev` | `DB_URL` | `DB_URL` |
| DB_URL required | no | no | yes | yes |
| Hikari pool | 5 | 5 | 10 | 20 |
| App log level | DEBUG | DEBUG | INFO | INFO, root WARN |
| SQL logging | on | on | off | off |
| Actuator | health, info, metrics, prometheus | health, info, metrics, prometheus | health, prometheus | health, prometheus |
| Health details | always | always | never, components only | never, components only |
| Swagger UI | on | on | off | off |
| JWT secret | `JWT_SECRET` or a development default | `JWT_SECRET` or a development default | `JWT_SECRET` | `JWT_SECRET` |
| Workload service token secret | `WORKLOAD_JWT_SECRET` or a development default | `WORKLOAD_JWT_SECRET` or a development default | `WORKLOAD_JWT_SECRET` | `WORKLOAD_JWT_SECRET` |
| CORS origins | `localhost:3000`, `localhost:4200` | `localhost:3000`, `localhost:4200` | none | none |

A fifth profile, `standalone`, is the default inside the Docker image: in-memory H2, the development JWT secret and the same CORS origins as local. See Running in Docker.

`DB_USERNAME` and `DB_PASSWORD` are required everywhere except standalone. The token lifetime (`gymcrm.jwt.ttl`, 1h) and the lockout settings (`gymcrm.security.max-login-attempts`, 3, and `gymcrm.security.lock-duration`, 5m) are shared by every profile.

stg and prod have no fallback URL or secret on purpose, and they refuse to start without them:

```
java.lang.IllegalStateException: The prod profile has no default database url: set the DB_URL
environment variable to the jdbc url of the prod database
```

```
java.lang.IllegalStateException: The prod profile has no default token secret: set the JWT_SECRET
environment variable to a random value of at least 32 bytes
```

A secret shorter than 32 bytes also stops the startup, because HS256 needs a 256 bit key.

That check runs before any bean is built. Without it the unresolved placeholder reaches Hikari, which only says `'url' must start with "jdbc"` and never mentions the variable.

## Database migrations

Flyway owns the schema. Two migrations in `src/main/resources/db/migration`:

- `V1__create_schema.sql` creates the six tables, written to run on PostgreSQL and on H2 in PostgreSQL mode.
- `V2__seed_training_types.sql` inserts the five training types.

Every profile and the test setup run with `spring.jpa.hibernate.ddl-auto: validate`. Hibernate never creates or changes anything, it only checks that the tables match the entities and refuses to start if they do not. Nothing is dropped on start, so data survives restarts.

Tests run Flyway against H2 too, which means every test run also checks the migrations against the entities.

If you have a `gymcrm` database left over from the older Hibernate setup, drop it and create it again once. Flyway refuses to run on a non-empty schema that has no `flyway_schema_history` table. Do not reach for `baseline-on-migrate` to get past it: baselining tells Flyway to assume the existing schema already matches V1, which for a Hibernate generated schema is a guess, and any difference then shows up later as a validation failure or a broken query.

## API docs

Swagger UI is at `http://localhost:8080/swagger-ui.html`, which redirects to `/swagger-ui/index.html`. The OpenAPI document is at `/v3/api-docs`.

The spec is generated from the Swagger annotations on the controllers. It declares `bearerAuth` as an HTTP bearer scheme with JWT format, so the UI shows an Authorize button. Paste a token from registration or login once and every secured endpoint sends it. The two registration endpoints carry no security block, so they show no padlock.

Both are off on stg and prod, through `springdoc.api-docs.enabled: false` and `springdoc.swagger-ui.enabled: false`. The spec and the UI return 404 there.

## Actuator

Exposed per profile as in the table above. Actuator is public: the security chain lets `/actuator/**` through without a token, so a Prometheus scraper or a load balancer health check needs no credentials. On stg and prod the exposure is cut to health and prometheus, and health shows which components exist without their details.

```bash
curl http://localhost:8080/actuator/health
```

```json
{"status":"UP","components":{
  "trainingTypes":{"status":"UP","details":{"count":5}},
  "databaseLatency":{"status":"UP","details":{"latencyMs":3,"thresholdMs":500}}}}
```

Trimmed to the two custom components. The real response also carries Boot's own `db`, `diskSpace`, `ping`, `ssl`, `livenessState` and `readinessState`.

Two custom health indicators:

- `trainingTypes` counts the rows in `training_types`. Every trainer needs a specialization, so an empty table makes registration impossible even when the database is fine. UP with the count, DOWN when the table is empty or the query throws.
- `databaseLatency` times one `select 1`. Boot's own `db` indicator answers whether the database is reachable, this one answers whether it is still fast enough. DOWN above the threshold or when the query throws. The threshold is `gymcrm.health.db-latency-threshold-ms`, default 500.

Both log at WARN only when they report DOWN. Health is polled constantly, so UP is silent.

Four meters, all tagged `application="gym-crm"`:

| Meter | Type | Tags |
|---|---|---|
| `gymcrm.registrations` | counter | `role` = trainee or trainer |
| `gymcrm.logins` | counter | `outcome` = success, failure or locked |
| `gymcrm.trainings.added` | counter | none |
| `gymcrm.training.duration` | distribution summary, minutes | none |

All of them are registered at startup, so they appear in a scrape at 0 before the first event instead of being missing.

```bash
curl -s http://localhost:8080/actuator/prometheus | grep gymcrm_
```

```
gymcrm_logins_total{application="gym-crm",outcome="failure"} 0.0
gymcrm_logins_total{application="gym-crm",outcome="locked"} 0.0
gymcrm_logins_total{application="gym-crm",outcome="success"} 0.0
gymcrm_registrations_total{application="gym-crm",role="trainee"} 1.0
gymcrm_registrations_total{application="gym-crm",role="trainer"} 0.0
gymcrm_training_duration_minutes_count{application="gym-crm"} 0
gymcrm_training_duration_minutes_sum{application="gym-crm"} 0.0
gymcrm_trainings_added_total{application="gym-crm"} 0.0
```

The login counter counts calls to `POST /auth/login` only. Requests with a bearer token do not touch it. `failure` is a wrong username or password, `locked` is an attempt while the username is locked out. A body with a blank username or password is rejected as a bad request before the check and is not counted.

No usernames in tags. One time series per user would be unbounded.

## Running in Docker

You need Docker Desktop. Nothing else: no Java, no Maven, no PostgreSQL on the host.

### The image on its own

```bash
docker build -t gym-crm:latest .
docker run --rm -d --name gym-crm -p 8080:8080 gym-crm:latest
```

That starts the `standalone` profile, which the image sets as its default. Standalone means
in-memory H2 inside the container: no database to connect to, Flyway builds the schema on every
start, and the data is gone when the container stops. Good for a demo or a smoke test, useless
for anything you want to keep.

```bash
docker stop gym-crm
```

### With a PostgreSQL container

Copy the example file and put a password in it:

```bash
cp .env.docker.example .env.docker
```

`.env.docker` is git ignored. It holds `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` for
the database container and `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for the app.

Plain docker, in order:

```bash
docker network create gymcrm-net

docker run -d --name gymcrm-db --network gymcrm-net \
  -v gymcrm-pgdata:/var/lib/postgresql/data \
  --env-file .env.docker postgres:17.11

docker exec gymcrm-db pg_isready -U gymcrm

docker run -d --name gym-crm --network gymcrm-net -p 8080:8080 \
  --env-file .env.docker -e SPRING_PROFILES_ACTIVE=dev gym-crm:latest
```

To tear that down and keep the data:

```bash
docker rm -f gym-crm gymcrm-db
docker network rm gymcrm-net
```

### The full stack with compose

`compose.yml` runs six services on the `gymcrm-net` network:

| Service | Container | Port on the host | Health |
|---|---|---|---|
| `db` | `gymcrm-db` | none | `pg_isready` |
| `artemis` | `artemis` | 8161 (console, 127.0.0.1 only) | `artemis check node --up` |
| `mongo` | `gymcrm-mongo` | none | mongosh `ping` |
| `discovery-server` | `discovery-server` | 8761 | http://localhost:8761/actuator/health |
| `workload-service` | `workload-service` | 8081 | http://localhost:8081/actuator/health |
| `app` | `gym-crm` | 8080 | http://localhost:8080/actuator/health |

Each Spring service is its own image, built from its own folder. Compose also needs a second
env file for the service secret:

```bash
cp .env.services.docker.example .env.services.docker
```

It holds `WORKLOAD_JWT_SECRET`, the secret gym-crm signs its service token with and
workload-service checks it with; generate one with `openssl rand -base64 48`. It is git ignored
like `.env.docker`. A third one, `.env.broker.docker` from `.env.broker.docker.example`, holds the broker
credentials (see Messaging, Docker). Two more, `.env.mongo-root.docker` and `.env.mongo-app.docker`, hold
the MongoDB root account and the app user (see NoSQL, Docker). The app gets the database, service and
broker files. workload-service gets the service, broker and Mongo app files, so it never sees the
PostgreSQL credentials or the Mongo root account.

```bash
docker compose up -d --build
docker compose ps
docker compose down
```

`docker compose down` leaves the volumes alone, so the data survives. Add `-v` when you want the
databases wiped too:

```bash
docker compose down -v
```

Nothing is assumed ready just because its container started. Every service has a healthcheck,
probed every 2 seconds while it starts, and `depends_on` waits for `service_healthy`: the app
waits for the database, Eureka and the broker, workload-service waits for Eureka, the broker and MongoDB. The
app does not wait for workload-service. Changes queue up in `trainer.workload` while it is missing,
and only the monthly read, which it finds through Eureka by the name `workload-service`, answers 503.

Both clients get `EUREKA_URL=http://discovery-server:8761/eureka` and register under their
compose service name through `EUREKA_INSTANCE_HOSTNAME` (`app`, `workload-service`; `localhost`
outside Docker). No container IPs appear anywhere. The Eureka dashboard is at
http://localhost:8761. The three Spring services restart `unless-stopped`; when Eureka comes
back, both clients re-register on their next 5 second heartbeat. Eureka serves reads without
its 30 second read-only cache, so a registration shows up on the next client fetch.

workload-service no longer runs on its own. Its image defaults to the `docker` profile, which has no
fallback for `WORKLOAD_JWT_SECRET`, `EUREKA_URL`, `ARTEMIS_URL`, `ARTEMIS_USER`, `ARTEMIS_PASSWORD` or
`MONGO_URI`. It also creates its indexes at startup, so it needs a reachable MongoDB and does not start
without one. Run it through compose, which provides the broker and MongoDB. Without a reachable Eureka it
still starts and answers health, and keeps retrying in the background.

### Why gymcrm-db and not localhost

`DB_URL` points at `jdbc:postgresql://gymcrm-db:5432/gymcrm`. Inside a container, `localhost` is
that container, so `localhost:5432` would be the app itself. On a user defined network, Docker
resolves container names, so `gymcrm-db` is the database container. You can see it:

```bash
docker exec gym-crm getent hosts gymcrm-db
```

The database publishes no port. Nothing outside the network can reach it, and it cannot clash
with a PostgreSQL already running on the host. Reach it through the app, or with psql inside the
container:

```bash
docker exec gymcrm-db psql -U gymcrm -d gymcrm -c "select username from users;"
```

### Logs

gym-crm logs to the console and to a rolling file at `/app/logs/gym-crm.log`, capped at
10 MB with 3 files kept. Only the image turns the file on, through `LOGGING_CONFIG` and
`LOGGING_FILE_NAME`, so running the app on your machine writes no files.

```bash
docker logs gym-crm --tail 20
docker exec -it gym-crm bash
tail -n 20 /app/logs/gym-crm.log
```

Every request carries a transaction ID, and you can choose it yourself, which makes one request
easy to find in the file:

```bash
curl -s -X POST http://localhost:8080/trainees \
  -H "Content-Type: application/json" -d '{"firstName":"Log","lastName":"Case"}'

curl -i http://localhost:8080/training-types \
  -H "Authorization: Bearer the-token-from-the-registration-response" \
  -H "X-Transaction-Id: demo-123"

docker exec gym-crm grep demo-123 /app/logs/gym-crm.log
```

workload-service does the same, into `/app/logs/workload-service.log`, and logs the transaction
id gym-crm forwards, so one id follows a request across both services:

```bash
docker logs gym-crm 2>&1 | grep demo-123
docker logs workload-service 2>&1 | grep demo-123
```

Passwords and tokens are masked and headers are never logged, so the file holds no credentials.

### The image

Multi-stage: a Maven stage builds the jar, and the runtime stage carries only a JRE and the app.
The jar is extracted into layers with Boot's jarmode tools, dependencies first and application
last, so a code change rebuilds a few hundred kB instead of the whole thing. It runs as the
non-root user `gymcrm`, and a HEALTHCHECK calls `/actuator/health`, which is what compose waits
on.

The discovery-server and workload-service images are built the same way and run as the
non-root users `discovery` and `workload`.

## Trying it

`postman/gym-crm.postman_collection.json` holds 82 requests with assertions on every one. Ten of them, prefixed "Workload - ", cover the trainer workload endpoint, trainee deletion (which sends the DELETE changes) and a direct call to workload-service without a token (401). Import it, start the app with the local or dev profile, and run the whole collection with the Collection Runner. It registers fresh users on every run, so you can run it again without restarting.

The registration and login requests save the returned tokens into collection variables, and every other request sends the matching one as `Authorization: Bearer`. You never copy a token by hand. The collection also walks through the lockout (three wrong logins, then 429), logout, and a CORS preflight. The CORS check only passes on local and dev, the profiles with allowed origins.

## Endpoints

| PDF | Method | Path | Auth | Success |
|---|---|---|---|---|
| 1 | POST | `/trainees` | none | 201, returns a token |
| 2 | POST | `/trainers` | none | 201, returns a token |
| 3 | POST | `/auth/login` | none, credentials in the body | 200, returns a token |
| 4 | PUT | `/users/{username}/password` | token, self, old password in body | 200 |
| 5 | GET | `/trainees/{username}` | token, self | 200 |
| 6 | PUT | `/trainees/{username}` | token, self | 200 |
| 7 | DELETE | `/trainees/{username}` | token, self | 200 |
| 8 | GET | `/trainers/{username}` | token, self | 200 |
| 9 | PUT | `/trainers/{username}` | token, self | 200 |
| 10 | GET | `/trainees/{username}/unassigned-trainers` | token, self | 200 |
| 11 | PUT | `/trainees/{username}/trainers` | token, self | 200 |
| 12 | GET | `/trainees/{username}/trainings` | token, self | 200 |
| 13 | GET | `/trainers/{username}/trainings` | token, self | 200 |
| | GET | `/trainers/{username}/workload/{year}/{month}` | token, self | 200, minutes read from workload-service; 503 when it is unavailable |
| 14 | POST | `/trainings` | token, caller is the trainee or the trainer | 200 |
| 15, 16 | PATCH | `/users/{username}/status` | token, self | 200 |
| 17 | GET | `/training-types` | token | 200 |
| | POST | `/auth/logout` | token | 204 |

The PDF lists activation for trainees and trainers as two endpoints. The active flag lives on the `users` table, and usernames are unique across both, so it is one endpoint here and one method in the code.

URLs use plural nouns and the username as the identifier. Usernames never change, so a URL never goes stale. Things that only touch the `users` table sit under `/users`, profiles under `/trainees` and `/trainers`.

### Status codes worth knowing

Registration returns 201 with a `Location` header pointing at the new profile, and a body with the username, the generated password and a token. The password is shown this one time, the database only keeps its hash. DELETE and POST `/trainings` return 200 with an empty body, because the PDF says 200 for both.

### Idempotency

PUT is idempotent everywhere. Sending the same body twice leaves the same state and returns the same response. That includes `isActive` inside the profile updates: sending the current value again is not an error.

PATCH `/users/{username}/status` is the one deliberately non-idempotent call, as the task requires. Asking for the state the user is already in returns 409.

POST is not idempotent. A second registration with the same name creates `John.Smith1`, a second POST `/trainings` creates a second training.

### Behavior decisions

`PUT /trainees/{username}` replaces the profile. Leaving out `dateOfBirth` or `address` clears them.

`PUT /trainees/{username}/trainers` replaces the whole trainer list. An empty list removes every trainer. An unknown username fails the whole request with 404 and names every missing trainer.

`GET /trainees/{username}/unassigned-trainers` only lists active trainers.

A trainer's specialization is set at registration and is read only after that. A specialization sent to `PUT /trainers/{username}` is ignored, the same as any other unknown field.

Specializations and type filters use the type name, like `YOGA`, as listed by `GET /training-types`. Names are constant and the same in every database. The ids are generated in insert order, so they could differ between environments.

The type of a training is not sent. It is always the trainer's specialization, so a training and its trainer can never disagree.

The name filters on the training lists match the first name, the last name, or the full name, case insensitive and partial. `miller`, `Sarah` and `Sarah Mil` all find Sarah Miller.

## Authentication

Spring Security guards every request. The two registrations, `POST /auth/login`, actuator and the API docs are open; everything else needs a JWT in the `Authorization: Bearer` header.

**Getting a token.** Registration returns one straight away. After that, `POST /auth/login` with `{"username": "...", "password": "..."}` returns `{"token": "..."}`. Spring's `DaoAuthenticationProvider` checks the password through `GymUserDetailsService`, which reads the user from `UserDao`. A wrong password and an unknown username give the same 401 "Invalid username or password", so the API does not reveal which usernames exist.

**The token.** HS256, signed with `JWT_SECRET`, built and parsed with jjwt in `JwtService`. It carries the username as `sub`, `iat`, `exp` (one hour by default) and a random `jti`. Nothing secret is in it: a JWT is signed, not encrypted, and anyone can read its payload.

**Checking it.** `JwtAuthenticationFilter` runs inside the security chain on every request. No `Bearer` header: it does nothing, and a protected endpoint answers 401 later. A valid token: it puts the user into the security context. An expired, tampered, malformed or revoked token: it stops the request with 401 and `WWW-Authenticate: Bearer error="invalid_token"`. The filter is created inside `SecurityConfig` instead of being a bean, because Boot would otherwise also register it as a plain servlet filter and it would run twice.

**Only your own data.** Every endpoint with `{username}` in the path has `@PreAuthorize("#username == authentication.name")`. A valid token on someone else's path gives 403. `POST /trainings` takes both people from the body, so its rule is that the caller must be the trainee or the trainer of that training.

**Passwords.** Stored as BCrypt hashes. BCrypt generates a random salt per hash and keeps it inside the hash, so two users with the same password get different values. Login and the password change both compare through the `PasswordEncoder`. Changing the password needs a token for that user and the correct old password; a wrong old password is 401.

**Brute force protection.** `LoginAttemptService` counts failed logins per username. The third failure locks the username for five minutes. While it is locked, `POST /auth/login` answers 429 with a `Retry-After` header in seconds and does not check the password at all, so even the right password is refused. A successful login resets the count. Unknown usernames are counted and locked exactly like real ones. The counts live in memory, so they reset when the app restarts.

**Logout.** `POST /auth/logout` with the token returns 204 (only POST; `GET /auth/logout` is a 404 and logs nobody out), and that token is refused from then on. It goes through Spring Security's logout support: a `LogoutHandler` adds the token's `jti` to `TokenRevocationService`, an in-memory deny list that drops each entry once the token would have expired anyway. Logout ends the token it was called with; other tokens of the same user stay valid until they expire. Without a valid token, logout is 401.

**CORS.** Configured in the security chain. Allowed origins come from `gymcrm.cors.allowed-origins` per profile. The allowed headers are `Authorization`, `Content-Type` and `X-Transaction-Id`; `X-Transaction-Id` and `Location` are exposed to the browser. Preflight requests need no token. No cookies are used, so CSRF protection is off and the session is stateless.

**Deactivated users** can still log in. Deactivation only takes a trainer off the list of trainers a trainee can pick, and if it blocked login, a deactivated user could never activate themselves again.

## Errors

Every error is an RFC 9457 problem detail with content type `application/problem+json`. This is a real response to `PATCH /users/Err.Case/status` with an empty body:

```json
{"detail":"Validation failed","instance":"/users/Err.Case/status","status":400,"title":"Bad Request","errors":{"isActive":"must not be null"}}
```

There is no `type` field. Spring leaves it out while it is the default `about:blank`.

| Status | When |
|---|---|
| 400 | Validation failed (with an `errors` map), malformed JSON, bad input like an unknown training type or a from date after the to date |
| 401 | Missing, invalid, expired or revoked token, wrong login credentials, wrong old password. Token problems carry `WWW-Authenticate: Bearer` |
| 403 | Valid token, but the data belongs to someone else |
| 404 | Profile or trainer not found, or an unknown URL called with a valid token. Without a token an unknown URL is 401, because authentication is checked before the URL is resolved |
| 409 | Status already set, or a username taken by a parallel registration |
| 429 | Login while the username is locked, with `Retry-After` |
| 500 | Anything unexpected. The detail is a fixed "Unexpected error", never the exception message |

Unknown URLs return the same JSON shape, not Tomcat's HTML error page. So do 401 and 403 from the security chain: they are written by `ProblemAuthenticationEntryPoint` and `ProblemAccessDeniedHandler` through one shared `ProblemDetailWriter`, because errors raised in the filter chain never reach `GlobalExceptionHandler`.

## workload-service API

Swagger UI is at `http://localhost:8081/swagger-ui.html`, the spec at `/v3/api-docs`. Everything else
needs a service token; `/actuator/health` and the docs are open.

| Method | Path | Scope | Success |
|---|---|---|---|
| POST | `/workloads` | `workload:write` | 200, empty body |
| GET | `/workloads/{username}` | `workload:read` | 200, years and months in ascending order |
| GET | `/workloads/{username}/{year}/{month}` | `workload:read` | 200, `trainingSummaryDuration` for that month |

`POST /workloads` takes `trainerUsername`, `trainerFirstName`, `trainerLastName`, `isActive`,
`trainingDate` (yyyy-MM-dd), `trainingDuration` and `actionType` (`ADD` or `DELETE`). Durations are
in minutes, everywhere, and must be positive. The names and the status in the body replace the stored ones.

- `ADD` adds the duration to the month of the training date.
- `DELETE` subtracts it and clamps at zero: deleting more than the month holds leaves 0, logs a WARN
  and still answers 200. A month that reaches 0 is dropped from the summary.
- `DELETE` for a trainer that has no workload is accepted and changes nothing (WARN, 200). It must not
  create a record, and a lost ADD must not make later deletes fail.
- `GET` for a trainer that was never recorded is 404. For a known trainer a month with nothing in it is 0.

| Status | When |
|---|---|
| 400 | A field is missing, blank or not positive, an unknown `actionType` (the message lists the allowed ones), a month outside 1 to 12, a non numeric year or month |
| 401 | Token missing, expired, tampered, or with the wrong issuer or audience |
| 403 | Valid token without the scope the endpoint needs |
| 404 | `GET` for a trainer with no workload |
| 409 | `POST` still hit a version conflict after 3 retries (see NoSQL, Update flow) |

Tokens are HS256 with `iss` `gym-crm` and `aud` `workload-service`, signed with `WORKLOAD_JWT_SECRET`
(at least 32 bytes). Errors are problem details, same as gym-crm. The docker profile has no fallback for
the secret or for `EUREKA_URL`.

## How gym-crm sends changes

Changes leave from a `@TransactionalEventListener(AFTER_COMMIT)` (`WorkloadChangeListener`), on the
request thread. A rolled back transaction therefore never reports anything, and nothing is sent while the
database transaction is still open.

- `POST /trainings` publishes one `ADD` for the new training.
- Deleting a trainee publishes one `DELETE` per training, loaded before the delete removes them. This is
  the only way trainings are deleted: there is no delete-training endpoint.
- A trainee without trainings sends nothing.

`WorkloadPublisher` sends each change as a JMS message to `trainer.workload`, with retry; the contract,
the error handling and the scaling are under Messaging.

**Reads.** The client is a `RestClient` behind an `@HttpExchange` interface (`WorkloadClient`), `@LoadBalanced`,
and calls `http://workload-service`. The load balancer's `RestClient.Builder` is `@Bean(autowireCandidate = false)`,
otherwise Eureka's own HTTP transport would pick up the load balanced builder.

**Service token.** gym-crm mints its own token with `WORKLOAD_JWT_SECRET`, separate from the user secret:
`iss` and `sub` `gym-crm`, `aud` `workload-service`, five minutes lifetime, reused until 30 seconds before it
expires. The REST read carries one with scope `workload:read`, every message one with `workload:write`.

**Transaction id.** For the read, an interceptor copies the id from the MDC into `X-Transaction-Id`; for a
write it goes into the `transactionId` message property. Either way workload-service logs the id of the
gym-crm request that caused it. Because both happen on the request thread the MDC is still there.

`GET /trainers/{username}/workload/{year}/{month}` is self only like the other trainer endpoints. It checks
that the trainer exists in gym-crm (404), then asks workload-service; a 404 from there means no workload
yet and is answered as 0 minutes.

## Resilience

- **Timeouts** come from the HTTP client: connect 2s, read 3s (`gymcrm.workload.connect-timeout`,
  `read-timeout`).
- **Circuit breaker** `workload`: count based window of 10 calls, opens at 50% failures once 5 calls were
  recorded, stays open 10s and then moves to half open by itself, where 2 calls decide. Failures are 5xx,
  I/O errors (including timeouts) and `IllegalStateException`; 4xx responses do not count.
- **Writes** no longer go through the breaker; they are queued, see Messaging, Error handling.
- **Reads**: there is nothing to fall back to, so the same failure ends in 503 with a problem detail.
- **Thread pool and time limiter are disabled** (`disable-thread-pool`, `disable-time-limiter`). The default
  would run the call on another thread, where the MDC, and with it the transaction id, is gone. The HTTP
  timeouts do the job of the time limiter.
- **Health.** `/actuator/health` has a `circuitBreakers` component with the state, failure rate and call
  counts in its details. It always reports UP: a down workload-service must not make gym-crm look
  unhealthy, because compose would restart a container that is working and a load balancer would drop it,
  while the application serves everything except one endpoint. The state is for reading, not for deciding.

## Messaging

Since the Messaging module, workload changes travel through ActiveMQ Artemis. The trainer's monthly
read stays a synchronous REST call.

```
gym-crm --after commit, JMS--> [ trainer.workload ] --competing consumers--> workload-service
   |                              (anycast queue)                                 |
   |                                    | invalid, or 3 failed deliveries        |
   |                                    v                                         |
   |                           [ DLQ.trainer.workload ]                           |
   +----------------- GET /workloads/{username}/{year}/{month} (REST) ------------+
```

**Why async for writes.** A write is a notification that something already happened in gym-crm. The user
does not wait for its result, and it must not be lost while workload-service is down, slow or redeploying.
The queue holds it until a consumer takes it, the broker retries a failed delivery, and the two services
no longer need to be up at the same time. The REST write path lost every change made during an outage.

**Why REST for reads.** A read needs the answer now and has nothing to queue. A request and a reply over
messaging would only add a correlation id and a timeout to what HTTP already does. The read keeps the
circuit breaker and answers 503 when workload-service cannot respond. `POST /workloads` stays on
workload-service for direct use.

### Event contract

One JSON text message per change. The body:

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUID | new for every event; the idempotency key |
| `occurredAt` | ISO-8601 instant | when gym-crm published the change |
| `trainerUsername` | string | |
| `trainerFirstName`, `trainerLastName` | string | replace the stored names |
| `isActive` | boolean | |
| `trainingDate` | `yyyy-MM-dd` | the month it counts towards |
| `trainingDuration` | positive integer | minutes |
| `actionType` | `ADD` or `DELETE` | |

The JMS properties:

| Property | Value | Why |
|---|---|---|
| `eventType` | `workloadChange` | a logical type id; the services share no Java classes |
| `eventVersion` | `1` | lets a later version be rejected cleanly instead of misread |
| `transactionId` | id of the gym-crm request | the same id in the logs of both services |
| `authorization` | service JWT, scope `workload:write` | the listener checks it just as the REST API does |
| `JMSXGroupID` | trainer username | per-trainer ordering, see Scaling |

Sample files live in `src/test/resources/contract/` and `workload-service/src/test/resources/contract/`.
Tests on each side check them, so a change to one side that the other cannot read fails a build.

### Error handling

| Outcome | Cause | What happens |
|---|---|---|
| `APPLIED` | valid event with a valid token | the change is stored, the eventId is recorded and the session commits (ack) |
| `DUPLICATE` | an eventId already processed | logged at INFO and acknowledged; the stored totals do not change |
| `DEAD_LETTERED UNAUTHENTICATED` | `authorization` missing, tampered, wrong issuer or audience, or expired when the message was sent; no message timestamp | forwarded to `DLQ.trainer.workload` on delivery 1, never redelivered |
| `DEAD_LETTERED FORBIDDEN` | valid token without `workload:write` | same |
| `DEAD_LETTERED MALFORMED` | not text, wrong `eventType` or `eventVersion`, empty body, unreadable JSON | same |
| `DEAD_LETTERED INVALID` | readable JSON failing validation (missing field, non-positive duration) | same; `dlqDetail` lists the violations |
| transient failure | any unexpected exception in the listener | logged as `FAILED` at ERROR, the session rolls back, and the broker redelivers after 500 ms, then 1000 ms |
| permanent failure | the same exception on all 3 deliveries | the broker moves the message to `DLQ.trainer.workload` (address-settings, not code) |
| broker down, port refused | broker host up, nothing listening on 61616 | the publisher retries 3 times (200, 400, 800 ms), then logs ERROR; the request still returns 200, measured at **1.4 s** with 4 attempts |
| broker down, host unreachable | no route, or the broker container stopped | each attempt waits out the 1 s connect timeout, and the 3 s retry budget ends it; measured at **3.6 s** with 3 attempts (unroutable IP) and **3.1 s** with 2 attempts (compose `artemis` stopped) |

Each forwarded message carries `dlqReason`, `dlqDetail`, `originalQueue`, `transactionId` and, when the body
could be read, `eventId`. A message the broker dead-letters keeps its own properties, plus `_AMQ_ORIG_QUEUE`.
The publisher retries only on `JmsException`. A conversion failure is a bug, and sending it again cannot help.
The timeouts live in the broker URL (`connect-timeout-millis=1000&callTimeout=1000`).
`useTopologyForLoadBalancing=false` stops the client trying the topology connector and then the static one
with a 2 s pause between them; with that pause, a request took 6.5 s to fail.

### Token lifetime and queued messages

The service token in `authorization` lives five minutes, but a message can wait much longer: while
workload-service is down, or in the DLQ until someone re-drives it. The listener therefore asks whether
the token was valid when the broker accepted the message, not whether it is valid now.
`ServiceTokenVerifier` checks the signature, `iss`, `aud` and the `workload:write` scope as before. It then
checks `exp` and `nbf` against the message's JMS timestamp (`jms_timestamp`), with the same 60 s skew as
the REST API, and requires an `exp`. A message without a timestamp (absent or 0) is `UNAUTHENTICATED`
(`message timestamp is missing, so the token cannot be checked`). A token that had already expired when it
was sent is still `UNAUTHENTICATED`, and the detail names both times
(`Jwt expired at ..., message sent at ...`). `iat` is not checked, as on the REST path. Spring fills a
missing `iat` from `exp`, so a check on it would reject tokens that never carried one.

The JMS producer stamps the timestamp on send. The broker keeps it when it dead-letters a message after 3
failed deliveries and when `retryMessage` moves it back, so a re-drive is checked against the original send.
A copy that the listener itself forwards to the DLQ is a new message with a new timestamp. Those messages
were rejected as invalid, so re-driving them does not help anyway. Tests:
`appliesTokenThatExpiredWhileQueued`, `deadLettersTokenInvalidAtSendTime` (expired before the send, and no
timestamp), `appliesRedriveAfterTokenExpiry` (DLQ, then `retryMessages()` an hour after the token expired),
and `ServiceTokenVerifierTest` for the skew and `nbf`. The tests send with a chosen timestamp through the
Artemis core client, because the JMS producer always stamps the current time.

### Acknowledgement and transactions

The listener container uses a transacted session (`sessionTransacted=true`). The message is acknowledged
when that session commits, after the change has been applied, so a crash in between means redelivery, not
loss. An invalid message is forwarded to the DLQ on the **same session**: the forward and the removal from
`trainer.workload` commit together, so the message is never in both queues and never in neither. An
unexpected exception propagates, the session rolls back, and the redelivery settings decide what happens
next. They live in `docker/artemis/broker.properties` and, for tests, in `EmbeddedBrokerTestConfig`, with
the same values: 3 attempts, 500 ms delay, multiplier 2, at most 5000 ms.

On the producer side the send is made after the database commit. A rollback therefore never publishes,
and a broker outage never undoes the user's change.

### Scaling

- **Competing consumers.** `trainer.workload` is anycast, so each message goes to exactly one consumer.
  More consumer threads, or more workload-service instances, share the load.
- **Concurrency per profile** through `workload.messaging.concurrency`: `2-4` by default (compose reads
  `WORKLOAD_MESSAGING_CONCURRENCY`), `1-1` in tests.
- **Message groups.** `JMSXGroupID` is the trainer username, so the broker sends all events of one
  trainer to one consumer at a time, in order, while other trainers are processed in parallel. A single
  consumer would also give ordering, but would not scale.
- **`consumerWindowSize=0`** on workload-service's URL. Consumers do not prefetch, so an idle consumer gets
  the next message instead of it waiting in a busy consumer's buffer.
- **Instances.** Since the NoSQL module every instance reads and writes the same MongoDB, so a read
  can reach any instance and more instances are fine. A message group that moves to another instance
  is safe as well: the version check and the in-document event ids cover the handover (see NoSQL).

### Profiles

| Service | Profile | Broker | URL | Credentials | Variable missing |
|---|---|---|---|---|---|
| gym-crm | local, dev, standalone | native | `ARTEMIS_URL`, default `tcp://localhost:61616` with the timeouts | `ARTEMIS_USER`, `ARTEMIS_PASSWORD`, or obviously fake defaults | uses the default |
| gym-crm | stg, prod | native | `ARTEMIS_URL` | `ARTEMIS_USER`, `ARTEMIS_PASSWORD` | fails at startup |
| gym-crm | tests | embedded, in-VM | none | none | n/a |
| workload-service | local | native | `ARTEMIS_URL`, same default | same, with fake defaults | uses the default |
| workload-service | docker | native | `ARTEMIS_URL` | `ARTEMIS_USER`, `ARTEMIS_PASSWORD` | fails at startup |
| workload-service | test | embedded, in-VM, with the production address-settings | none | none | n/a |

`RequiredBrokerVariablesProcessor` (one copy per service) stops the deployed profiles before any bean is
created. In compose, gym-crm runs `dev` and workload-service runs `docker`. Both get `ARTEMIS_URL` from
`compose.yml` and the credentials from `.env.broker.docker`. The test suites need no Docker.

### Docker

The `artemis` service runs `apache/activemq-artemis:2.44.0` (the client library is 2.43.0):

```bash
cp .env.broker.docker.example .env.broker.docker
```

Set `ARTEMIS_USER` and `ARTEMIS_PASSWORD` there (for example `openssl rand -base64 24`). The file is git
ignored. Anonymous login is off. Port 61616 stays on the compose network. Only the web console is
published, at http://127.0.0.1:8161/console, bound to localhost. Both services wait until
`artemis check node --up` passes.

- `docker/artemis/broker.properties` declares both anycast queues and the address-settings. It is passed
  with `artemis run --properties`, because the image copies `etc-override` only when it first creates the
  instance, and after that the instance lives on in the volume.
- `docker/artemis/log4j2.properties` is the image's logging config plus one filter, picked through
  `JAVA_ARGS_APPEND=-Dlog4j2.configurationFile=...`. When the broker dead-letters a message it logs
  AMQ222149 with **every** message property, including `authorization`, so a live service token would
  land in the log. A `StringMatchFilter` on `AMQ222149`, scoped to the
  `org.apache.activemq.artemis.core.server` logger, drops only that line; every other server warning
  stays. Checked live: with the filter off, the line printed a marker token; with it on, nothing.
- The named volume `gymcrm-artemis-data` holds the whole instance, so messages survive `docker compose down`
  and restarts. Mounting it on `data/` alone fails, because Docker creates that directory owned by root.
- **Changing the password.** The image writes the credentials into the instance the first time it
  creates it. A new password in `.env.broker.docker` therefore needs
  `docker compose down && docker volume rm gymcrm-artemis-data`, which also drops queued messages.

Queue counts are also readable over Jolokia from the host, with basic auth (`GET` on
`/console/jolokia/read/<queue mbean>/MessageCount`).

### Live scenarios

The helpers in `scripts/messaging/` run against the compose stack:

- `demo.sh` keeps usernames, generated passwords and user tokens in a mode 600 file under
  `~/.cache/gym-crm/` and never prints them.
- `mint.sh` signs a five minute service token inside the gym-crm container, with that container's secret.
- `produce.sh` sends a raw message with the artemis CLI inside the artemis container.

The token goes from one container to the other through a pipe. The broker credentials never leave the
artemis container.

```bash
scripts/messaging/demo.sh setup && scripts/messaging/demo.sh login
```

**a. Happy path.** 200, the month shows 60, and the queue shows 0 messages with ADDED equal to ACKED.

```bash
scripts/messaging/demo.sh add 2026-11-03 60 demo-a-001
scripts/messaging/demo.sh read 2026 11
scripts/messaging/demo.sh q
docker logs gym-crm 2>&1 | grep demo-a-001; docker logs workload-service 2>&1 | grep demo-a-001
```

**b. Consumer down.** Both POSTs return 200 and the queue holds 2. Both are applied about 2 s after the
start. The read answers 503 until Eureka has the instance again (about 6 s), then 75.

```bash
docker compose stop workload-service
scripts/messaging/demo.sh add 2026-12-01 30 demo-b-001
scripts/messaging/demo.sh add 2026-12-02 45 demo-b-002
scripts/messaging/demo.sh q
docker compose start workload-service
scripts/messaging/demo.sh read 2026 12
```

**c. Broker down.** 200 in 3.1 s with 2 WARN and 1 ERROR (`attempts=2`), and that change is lost (no
outbox). After the start gym-crm publishes again without a restart. The listener reconnects within
about 5 s, its recovery interval, and was measured at 4.8 s.

```bash
docker compose stop artemis
scripts/messaging/demo.sh add 2026-10-05 20 demo-c-001
docker logs gym-crm 2>&1 | grep demo-c-001 | grep -E "WARN|ERROR"
docker compose start artemis
scripts/messaging/demo.sh add 2026-10-06 25 demo-c-002
```

**d. Persistence.** The queue shows 1 before and after the broker restart, and the change is applied once
the consumer is back.

```bash
docker compose stop workload-service
scripts/messaging/demo.sh add 2027-01-10 50 demo-d-001
scripts/messaging/demo.sh q
docker compose restart artemis
scripts/messaging/demo.sh q
docker compose start workload-service
```

**e. Invalid message.** Without a token the message is dead-lettered on delivery 1 as `UNAUTHENTICATED`,
with `dlqDetail` `authorization header is missing`. With a valid token the same body is `MALFORMED`.
`Rollback.java` shows the broker side: three rolled back deliveries, then the DLQ, with no AMQ222149
line logged. Its header has the one-line build-and-run command.

```bash
scripts/messaging/produce.sh --no-token demo-e-001 1 '{not json'
scripts/messaging/mint.sh | scripts/messaging/produce.sh demo-e-002 1 '{not json'
scripts/messaging/demo.sh dlq
```

**f. Duplicate.** The same message is sent twice with a real service token. The first is `APPLIED`, the
second `DUPLICATE`, and the month shows 40.

```bash
BODY=$(scripts/messaging/demo.sh event 2027-02-14 40)
scripts/messaging/mint.sh | scripts/messaging/produce.sh demo-f-001 2 "$BODY"
docker logs workload-service 2>&1 | grep demo-f-001
scripts/messaging/demo.sh read 2027 2
```

**g. Scaling.** `3-3` shows 3 consumers on the queue. The default `2-4` shows 2, and health reports
`maxConsumers` 4.

```bash
WORKLOAD_MESSAGING_CONCURRENCY=3-3 docker compose up -d workload-service
scripts/messaging/demo.sh q
docker compose up -d workload-service
scripts/messaging/demo.sh health
```

**h. Health.** With the broker stopped, both services still answer 200 UP. gym-crm shows
`reachable: false`. workload-service shows `connected: false, recovering: true`.

```bash
docker compose stop artemis
scripts/messaging/demo.sh health
docker compose start artemis
```

### One change through both services

Real lines from `docker logs gym-crm` and `docker logs workload-service` after
`scripts/messaging/demo.sh add 2027-03-02 35 readme-msg-001`. The service name is added in front, and the
SQL and body DEBUG lines are left out:

```
gym-crm           INFO  [readme-msg-001] c.g.gymcrm.service.TrainingService - Added training 'Demo' for trainee Demo.Member2 with trainer Demo.Coach2
gym-crm           INFO  [readme-msg-001] c.g.g.workload.WorkloadPublisher - Published workload event: transactionId=readme-msg-001 queue=trainer.workload eventId=c92b9869-b2dc-43fb-b612-5a1775c007e2 action=ADD trainer=Demo.Coach2 attempts=1 durationMs=15
gym-crm           INFO  [readme-msg-001] c.g.gymcrm.web.RequestLoggingFilter - POST /trainings returned 200 in 28 ms
workload-service  INFO  [readme-msg-001] c.g.workload.service.WorkloadService - Applied ADD of 35 min in 2027-03 for trainer Demo.Coach2, month total is now 35 min
workload-service  INFO  [readme-msg-001] c.g.w.m.WorkloadEventListener - trainer.workload event c92b9869-b2dc-43fb-b612-5a1775c007e2 ADD for trainer Demo.Coach2: APPLIED in 92 ms, delivery 1
```

The id comes from the request's `X-Transaction-Id`, travels as the `transactionId` property, and is
put on the MDC of the listener thread.

## NoSQL

Since the NoSQL module, workload-service keeps its totals in MongoDB instead of in memory. A restart
loses nothing, and every instance reads and writes the same data. The `TrainerWorkloadRepository`
interface stayed. `MongoTrainerWorkloadRepository` replaced the in-memory implementation, and the
service, the listener and the controller did not change.

```
trainer.workload --> WorkloadEventListener --> WorkloadService --> TrainerWorkloadRepository
POST /workloads  --> WorkloadController   --/                              |
                                                              MongoTrainerWorkloadRepository
                                                                           |
                                                   MongoDB  workload.trainer_workloads
                                                   (one document per trainer)
```

### One document per trainer

Every read is about one trainer: `GET /workloads/{username}` returns all of that trainer's years, and the
monthly read returns one month of it. Every write also touches one trainer, adding to or subtracting from
one month. With the trainer, its years and its months in one document, a read is one lookup by the
unique username, and a write is one atomic single-document save. No join or aggregation is needed, and
no transaction across documents. The compose MongoDB is a standalone server, and multi-document
transactions would need a replica set. The document stays small: twelve short month entries a year
plus at most 100 event ids, far from the 16 MB limit.

A real document from the compose stack, read with mongosh as the app user:

```
{
  _id: ObjectId('6b0202b4b12d6211cafec5f1'),
  username: 'Demo.Coach4',
  firstName: 'Demo',
  lastName: 'Coach',
  status: true,
  years: [
    {
      year: 2026,
      months: [
        { month: 11, trainingSummaryDuration: Long('60') },
        { month: 12, trainingSummaryDuration: Long('80') }
      ]
    },
    {
      year: 2027,
      months: [ { month: 1, trainingSummaryDuration: Long('40') } ]
    }
  ],
  recentEventIds: [
    '4fac4aa4-ba3b-4ba2-92c1-d44d2afb3de4',
    '00708a79-435d-4aa8-ac15-730c9db978e7',
    '47c52f13-b06c-407f-b3d8-0ce44d18a471',
    'c51f4248-2430-4f1a-ae35-690a6975fc1e',
    'd86c2c8a-4235-49df-85f6-808e78f07b85'
  ],
  version: Long('4')
}
```

- `_id` is an ObjectId; the business key is `username`.
- `status` is a Boolean. The former `TrainerStatus` enum is gone.
- `trainingSummaryDuration` is in minutes, as everywhere else. A month that reaches 0 is removed, and
  `DELETE` still subtracts and clamps at zero.
- `recentEventIds` and `version` are explained below. There is no `_class` field.
  `MongoTypeMapperConfig` keeps Boot's converter and only sets a type mapper without a type key, because
  no document here is polymorphic. `storesNoTypeKey` checks the raw document.

### Indexes

| Name | Keys | Why |
|---|---|---|
| `username_unique` | `username` ascending, unique | every read and write looks a trainer up by username; uniqueness stops two documents for one trainer |
| `firstName_lastName` | `firstName`, `lastName` ascending | lookup by name, as the module asks for |

`spring.data.mongodb.auto-index-creation` stays `false`. `MongoIndexInitializer` creates both indexes by
name at startup, in `afterSingletonsInstantiated`, before the JMS listener containers start, so no message
is applied without the unique index. Creating an index that already exists with the same definition
changes nothing, so a restart is safe (`createsIndexesIdempotently`). Indexes are named and created
explicitly because annotation-driven creation is a side effect of the first use of the mapping context.
It does not appear in a review, and it cannot be ordered before the listeners start. Spring Data
has had it off by default since 3.0 for that reason. The startup log shows the result:

```
INFO  [] c.g.w.p.MongoIndexInitializer - MongoDB indexes ready on trainer_workloads: username_unique, firstName_lastName
```

### Update flow

Every change, from the queue or from `POST /workloads`, runs the same way:

1. Find the document by username.
2. If the event id is already in `recentEventIds`, stop: duplicate, nothing saved.
3. Apply the change in Java on the domain object (`ADD` adds, `DELETE` subtracts and clamps at zero).
4. Append the event id, keeping the last 100.
5. Save with the `version` read in step 1.

`version` is a Spring Data `@Version` field. The save only matches a document that still has the version
read in step 1, and it increments it. If another writer saved in between, the save matches nothing and
Spring Data throws `OptimisticLockingFailureException`. A Spring Framework 7 `RetryTemplate`
(`MongoConfig.versionConflictRetry`) then starts again from step 1 on the fresh document. It retries
only that exception, at most 3 times, after 20, 40 and 80 ms. Each retry is a WARN
(`Version conflict on the workload of trainer ..., retry 1 of 3`). If the last retry also conflicts, the
exception reaches the caller. The listener logs `FAILED` and the broker redelivers. `POST /workloads`
answers 409.

**Concurrent creation.** Two writers that both find no document both insert one, and the second insert
breaks the unique `username` index. That raises `DuplicateKeyException`, not a version conflict. The
adapter rethrows it as `OptimisticLockingFailureException`, so the same retry re-reads the document the
other writer created and applies the change on top of it. `retriesConcurrentCreateAgainstRealIndex`
runs this against the real index: 30 minutes written by a competitor plus 60 of our own end up as 90,
at version 1.

Message groups already keep one trainer's events on one consumer. Version conflicts therefore come from
`POST /workloads` racing an event, or from a group moving between consumers. The retry makes both safe.

### Idempotency inside the document

The idempotent consumer of the Messaging module kept its event ids in memory. They now live in the
trainer document, in `recentEventIds`, and are checked and appended in the **same versioned save** as the
change. The change and the record of having applied it are one single-document write, so they succeed or
fail together.

- No separate "processed events" collection. With one, a crash between the two writes would leave a
  change applied but not recorded, which applies it again on redelivery, or recorded but not applied,
  which loses it.
- A version conflict re-reads the document, so the id is checked again on the fresh copy. If the other
  writer was the same event, it is a duplicate.
- A failed save stores neither the change nor the id, so the redelivered event is applied exactly once.
- **The window is 100 ids per trainer** (`workload.persistence.recent-event-ids`, at least 1). Duplicates
  arrive soon after the original: broker redeliveries come within seconds, and a producer retry within its
  3 s budget. 100 ids are under 5 KB per trainer. A duplicate of an event older than the last 100 events
  of the same trainer would be applied again.
- REST writes carry no event id. `POST /workloads` changes the totals and keeps the stored ids as they are.

### Validation layers

| Layer | Where | Rejects | Result |
|---|---|---|---|
| Token | `WorkloadEventListener`, Spring Security on REST | missing, expired or tampered token, wrong scope | DLQ `UNAUTHENTICATED` or `FORBIDDEN`; 401 or 403 |
| Event or request | bean validation on `WorkloadEvent` and `@Valid WorkloadRequest` | missing or blank fields, non-positive duration, unknown action | DLQ `INVALID` or `MALFORMED`; 400 |
| Domain | `WorkloadChange.applyTo` | nothing; it decides the outcome | `DELETE` clamps at zero (WARN), `DELETE` for an unknown trainer creates nothing |
| Document | `ValidatingEntityCallback` before every save, on `TrainerWorkloadDocument`, `YearDocument`, `MonthDocument` | blank username or names, null status, month outside 1 to 12, negative total, blank event id | `ConstraintViolationException`; nothing reaches MongoDB |

The document layer is the last guard. It catches a mapping bug that the first two layers cannot see,
before it is stored. `rejectsMonthOutOfRange` and `rejectsNegativeDurationAndMissingStatus` show that
nothing is written.

### Profiles

| Profile | MongoDB | Connection | Variable missing |
|---|---|---|---|
| local | a server on `localhost:27017` | `MONGO_URI`, or an obviously fake development URI with `serverSelectionTimeoutMS=2000&connectTimeoutMS=2000` | uses the default |
| docker | the compose `mongo` service | `MONGO_URI` only | fails at startup: `The docker profile has no default MongoDB connection: set the MONGO_URI environment variable` |
| test | Testcontainers `mongo:8.0.38`, one container per JVM | `@ServiceConnection` | n/a |

Boot 4 reads `spring.mongodb.uri`; the `spring.data.mongodb.*` connection properties are gone.
`RequiredMongoVariablesProcessor` stops the docker profile before any bean is built. MongoDB is a core
dependency: its health contributor stays on, and `/actuator/health` is DOWN while MongoDB is unreachable.
The Testcontainers image is `MongoTestContainerConfig.MONGO_IMAGE`, the same tag as compose. The
workload-service test suite therefore needs Docker running. gym-crm and discovery-server do not.

### Docker

The `mongo` service runs `mongo:8.0.38`, the current 8.0 LTS. It has no host port: 27017 is reachable
from workload-service on `gymcrm-net` and nowhere else, and the demo runs mongosh inside the container.
Data lives in the named volume `gymcrm-mongo-data` on `/data/db`. The healthcheck is a mongosh `ping`,
and workload-service waits for `service_healthy`.

Two env files, both git ignored, each with a committed `.example`:

```bash
cp .env.mongo-root.docker.example .env.mongo-root.docker
cp .env.mongo-app.docker.example .env.mongo-app.docker
```

| File | Holds | Read by |
|---|---|---|
| `.env.mongo-root.docker` | `MONGO_INITDB_ROOT_USERNAME`, `MONGO_INITDB_ROOT_PASSWORD` | the `mongo` container only |
| `.env.mongo-app.docker` | `MONGO_APP_DATABASE`, `MONGO_APP_USERNAME`, `MONGO_APP_PASSWORD` | `mongo` (for the init script) and workload-service |

Generate the passwords with `openssl rand -hex 24`. workload-service never sees the root account.

- **Init script.** `docker/mongo/create-app-user.js` is mounted into `/docker-entrypoint-initdb.d`. It
  creates the app user with `readWrite` on the workload database only, reading every value from the
  environment, and fails if one is missing. The image runs init scripts **only when `/data/db` is
  empty**. Changing a password in the env files does nothing to an existing volume:
  `docker compose rm -s -f mongo && docker volume rm gymcrm-mongo-data` starts over and deletes the data.
- **The URI.** compose builds `MONGO_URI` with `$$` placeholders that Spring resolves from the app env
  file:
  `mongodb://${MONGO_APP_USERNAME}:${MONGO_APP_PASSWORD}@mongo:27017/${MONGO_APP_DATABASE}?authSource=${MONGO_APP_DATABASE}&serverSelectionTimeoutMS=2000&connectTimeoutMS=2000`.
  The user authenticates against the workload database, where the init script created it. The two
  timeouts make an outage fail in 2 s instead of the driver's default 30 s.
- **Downgrade from 8.3.** The first version of this module ran `mongo:8.3.17`. On that volume, 8.0.38
  crash-loops with `UPGRADE PROBLEM: Found an invalid featureCompatibilityVersion document`, because 8.3
  wrote the data files. MongoDB does not downgrade in place, so the volume had to be recreated with the
  command above.

### Live scenarios

Against the compose stack, with the `scripts/messaging/` helpers from Messaging, Live scenarios. Each
mongosh line runs as the app user inside the container, with the credentials from that container's
environment, so no password is typed or printed. To shorten the commands, define a shell function first:

```bash
app_mongosh() { docker exec gymcrm-mongo sh -c 'mongosh --quiet --norc "mongodb://localhost:27017/$MONGO_APP_DATABASE?authSource=$MONGO_APP_DATABASE" -u "$MONGO_APP_USERNAME" -p "$MONGO_APP_PASSWORD" --eval "$1"' sh "$1"; }
```

**a. Startup.** All six containers report healthy, and the workload-service log shows the indexes.

```bash
docker compose up -d --build --wait
docker logs workload-service 2>&1 | grep "indexes ready"
```

**b. A training lands in MongoDB.** 200, the month shows 60, and the document has nested years and
months, `status: true`, one id in `recentEventIds` and `version: 0`.

```bash
scripts/messaging/demo.sh setup && scripts/messaging/demo.sh login
scripts/messaging/demo.sh add 2026-11-10 60 nosql-b-1
scripts/messaging/demo.sh read 2026 11
app_mongosh 'db.trainer_workloads.findOne({username: "Demo.Coach4"})'
```

Use the trainer username that `demo.sh setup` printed.

**c. Indexes.** Prints `[ '_id_', 'username_unique (unique)', 'firstName_lastName' ]`.

```bash
app_mongosh 'db.trainer_workloads.getIndexes().map(i => i.name + (i.unique ? " (unique)" : ""))'
```

**d. Restart.** The read returns the same total after the restart. Right after it, the read can answer
503 for about 2 s, until gym-crm's Eureka registry has the instance again.

```bash
docker compose restart workload-service
scripts/messaging/demo.sh read 2026 11
```

**e. Duplicate.** The same event is sent twice. The log shows `APPLIED`, then `DUPLICATE`, and the count
of documents holding the id is 1.

```bash
BODY=$(scripts/messaging/demo.sh event 2027-01-20 40)
scripts/messaging/mint.sh | scripts/messaging/produce.sh nosql-e-dup 2 "$BODY"
docker logs workload-service 2>&1 | grep nosql-e-dup | grep -E "APPLIED|DUPLICATE"
EVENT_ID=$(printf '%s' "$BODY" | sed 's/.*"eventId":"\([^"]*\)".*/\1/')
app_mongosh "db.trainer_workloads.countDocuments({recentEventIds: \"$EVENT_ID\"})"
```

**f. MongoDB down.** `POST /trainings` still answers 200. workload-service logs `FAILED
DataAccessResourceFailureException` on each of the 3 deliveries, about 2.5 to 3 s apart (2 s server
selection timeout plus the 500 and 1000 ms redelivery delays). The broker then moves the message to
`DLQ.trainer.workload`, about 7.5 s after the send. Measured on the first run: deliveries at 12.5 s,
15.1 s and 18.1 s, then the DLQ. `/actuator/health` of workload-service is 503 DOWN with `mongo` DOWN,
which is expected for a core dependency. MongoDB was not back before the third delivery. Its restart
plus the driver's reconnect takes longer than the 6 s that the three deliveries span, so a real outage
ends in the DLQ (see Known limitations for the re-drive). After `start`, health is UP within about 3 s.
While workload-service is DOWN, Eureka takes it out of the load balancer, so the monthly read answers 503
(`No servers available for service: workload-service`) and recovers within seconds.

```bash
docker compose stop mongo
scripts/messaging/demo.sh add 2026-11-14 30 nosql-f-down
docker logs workload-service 2>&1 | grep nosql-f-down | grep FAILED
curl -s localhost:8081/actuator/health
scripts/messaging/demo.sh dlq
docker compose start mongo
```

**g. Least privilege.** The app user is refused outside its database:
`MongoServerError: not authorized on admin to execute command { listCollections: 1, ... }`.

```bash
app_mongosh 'db.getSiblingDB("admin").getCollectionNames()'
```

### One change into MongoDB

Real lines from `docker logs gym-crm` and `docker logs workload-service` after
`scripts/messaging/demo.sh add 2026-12-03 40 readme-nosql-001`. The service name is added in front, and
the timestamps, SQL and body DEBUG lines are left out:

```
gym-crm           INFO  [readme-nosql-001] c.g.gymcrm.service.TrainingService - Added training 'Demo' for trainee Demo.Member4 with trainer Demo.Coach4
gym-crm           INFO  [readme-nosql-001] c.g.g.workload.WorkloadPublisher - Published workload event: transactionId=readme-nosql-001 queue=trainer.workload eventId=00708a79-435d-4aa8-ac15-730c9db978e7 action=ADD trainer=Demo.Coach4 attempts=1 durationMs=23
gym-crm           INFO  [readme-nosql-001] c.g.gymcrm.web.RequestLoggingFilter - POST /trainings returned 200 in 79 ms
workload-service  INFO  [readme-nosql-001] c.g.w.p.MongoTrainerWorkloadRepository - Updated the workload of trainer Demo.Coach4 (active true) from version 0 to 1: year 2026 month 12 from 0 to 40 min
workload-service  INFO  [readme-nosql-001] c.g.workload.service.WorkloadService - Applied ADD of 40 min in 2026-12 for trainer Demo.Coach4, month total is now 40 min
workload-service  INFO  [readme-nosql-001] c.g.w.m.WorkloadEventListener - trainer.workload event 00708a79-435d-4aa8-ac15-730c9db978e7 ADD for trainer Demo.Coach4: APPLIED in 90 ms, delivery 1
```

The repository line is the storage operation: which trainer, the version before and after the save,
and the month before and after. The first change of a trainer logs `Created the workload of trainer ...
at version 0`. Names never appear in these lines; the `(active ...)` flag is the status.

## Discovery

Settings that make registration and eviction fast, so a demo that stops and starts a service shows the
effect within seconds. The defaults (30s heartbeat, 90s expiry, 30s registry cache) would take minutes.

| Setting | Where | Value | Why |
|---|---|---|---|
| `lease-renewal-interval-in-seconds` | clients | 5 | heartbeat |
| `lease-expiration-duration-in-seconds` | clients | 15 | a stopped service is evicted after three missed beats |
| `registry-fetch-interval-seconds` | clients | 5 | a client sees changes quickly |
| `instance-info-replication-interval-seconds` | clients | 5 | status changes reach Eureka quickly |
| `eviction-interval-timer-in-ms` | server | 5000 | expired leases are removed every 5s |
| `use-read-only-response-cache` | server | false | reads see a registration immediately |
| `enable-self-preservation` | server | false | Eureka keeps evicting instead of freezing the registry |
| `loadbalancer.cache.ttl` | gym-crm | 5s | the load balancer re-reads the instance list |

Self preservation off and these short leases suit a three service demo on one machine. In production
the defaults protect the registry from evicting everything during a network blip.

## Logging

Each request gets a transaction ID. If the request carries an `X-Transaction-Id` header with up to 64 letters, digits and dashes, that one is reused, otherwise a UUID is generated. Anything else in the header is replaced, so a caller cannot inject fake log lines. The ID goes into the MDC, so every log line of the request shows it, and it comes back in the response header. It is removed when the request ends, because Tomcat reuses threads.

One summary line per request: method, URL, status and time taken. INFO below 400, WARN for 4xx with the error body, ERROR for 5xx. Request and response bodies are logged at DEBUG.

Actuator and API docs requests are not logged at all. Prometheus scrapes every few seconds and the docs UI pulls the spec on every page load, and those lines would bury the real traffic. The skipped prefixes are `/actuator/`, `/swagger-ui.html`, `/swagger-ui/` and `/v3/api-docs`, kept in one list in `RequestLoggingFilter`. The transaction ID filter still runs for everything.

Any JSON field whose name ends in `password` or `token` is masked before it is logged, including the generated password and the token in a registration response. Headers are never logged, so the `Authorization` header never reaches the log.

Security events are logged with the transaction ID, and with the username where it is known: a successful login at INFO, a failed login, a lock, an attempt while locked and a rejected or revoked token at WARN, a denied request at WARN with the method, the path and the caller, a logout at INFO. `AccessDenialLog` writes that denial line in one place, so the security chain and the MVC layer produce the same one, for example `Denied GET /trainees/Other.Owner for user Logged.Denial`. Passwords, hashes and tokens never appear.

A failed request is logged once. Only the 500 handler adds a stack trace, because only a 500 is a bug.

Both services log on two levels, and the lines of one request share the transaction id. The **transaction**
level is the request line from `RequestLoggingFilter`: call, status, time. The **operation** level is
what the business code did. These are real lines from the compose stack, from
`docker logs gym-crm` and `docker logs workload-service` after a `POST /trainings` sent with
`X-Transaction-Id: readme-demo-002`, from the Microservices module, when the change still went over
`POST /workloads`; gym-crm forwarded the id, so both services show it. The same flow through the broker is
under Messaging, One change through both services:

```
gym-crm           INFO  [readme-demo-002] c.g.gymcrm.service.TrainingService - Added training 'Morning flow' for trainee Tom.Brown2 with trainer Sarah.Miller2
gym-crm           INFO  [readme-demo-002] c.g.gymcrm.web.RequestLoggingFilter - POST /trainings returned 200 in 18 ms
workload-service  INFO  [readme-demo-002] c.g.workload.service.WorkloadService - Applied ADD of 60 min in 2026-08 for trainer Sarah.Miller2, month total is now 60 min
workload-service  INFO  [readme-demo-002] c.g.w.web.RequestLoggingFilter - POST /workloads returned 200 in 5 ms, request {"trainerUsername":"Sarah.Miller2","trainerFirstName":"****","trainerLastName":"****","isActive":true,"trainingDate":"2026-08-01","trainingDuration":60,"actionType":"ADD"}
```

(The service name column and the timestamps are left out; the lines are otherwise as logged.)

`TransactionLoggingIntegrationTest` asserts that both lines carry the same id. Names are masked in the
body, as are passwords and tokens. A change that could not be published is one WARN per failed attempt and
a final ERROR, with the id of the user's request: `Workload event not published: transactionId=... queue=trainer.workload eventId=... action=ADD trainer=... attempts=2 durationMs=3108 cause=...`.

`logback-spring.xml` holds the console pattern, including the transaction ID. Levels are not in it, they live in the profile YAMLs under `logging.level`, so there is one place to look.

## How it's wired

`GymCrmApplication` is a plain `@SpringBootApplication`, plus `@ConfigurationPropertiesScan` so the `@ConfigurationProperties` records bind without being listed one by one.

- `FilterConfig` registers the two filters. Boot's character encoding filter runs first at `HIGHEST_PRECEDENCE`, then the transaction ID filter at `+1`, then request logging at `+2`. The order matters: the ID has to be on the MDC before the request log writes its line. Boot slots its own filters in between, so `FilterConfigTest` asserts the relative order rather than fixed positions.
- `SecurityConfig` holds the security filter chain, the BCrypt encoder, the `AuthenticationManager`, the CORS configuration and the logout setup. Inside the chain, `JwtAuthenticationFilter` sits before Spring's `LogoutFilter`, so logout already knows who is logging out. Our transaction ID and request logging filters wrap the whole chain, so a 401 from security is still logged with its transaction ID.
- `OpenApiConfig` carries the `@SecurityScheme` annotation the spec is generated from.
- `RequiredDatabaseUrlProcessor` and `RequiredJwtSecretProcessor` are `EnvironmentPostProcessor`s registered in `META-INF/spring.factories`. They share `RequiredDeployedVariableProcessor` and stop stg and prod before any bean is created when `DB_URL` or `JWT_SECRET` is missing.
- `StartupInfoLogger` writes one line on startup with the active profiles and the JDBC URL. The username and password are separate properties and never reach the log.

Injection is constructor based everywhere: final fields and one constructor per class, with no `@Autowired` on it. The only `@Autowired` left in the project marks which of the two constructors of `DatabaseLatencyHealthIndicator` Spring should use. Transactions sit on the services, one service method is one unit of work.

Controllers only talk to the facade and only return DTOs, never entities. The services no longer take passwords: who the caller is comes from Spring Security, and the services only receive the username.

Package rule: `security` holds what plugs into Spring Security or handles tokens (the filter, the entry point and access denied handler, `GymUserDetailsService`, `JwtService`, `TokenRevocationService`, the logout handlers). `service` holds the application rules, including `LoginService` and `LoginAttemptService`. Dependencies point one way, from `service` to `security`, never back.

## Fetching

Profile endpoints return the trainee with their trainers, or the trainer with their trainees. `findProfileByUsername` loads the whole graph in one query with `join fetch`, so reading a profile does not fire a query per trainer.

The collection side uses `left join fetch`. With a plain join, a trainee with no trainers would match no rows and come back as "not found".

`spring.jpa.open-in-view` is false, so the session closes with the service transaction. DTOs are built in the controller after that, so anything not fetched would throw `LazyInitializationException`. `ProfileFetchIntegrationTest` runs without a transaction on purpose to catch exactly that. Removing one `join fetch` from the profile query makes it fail with a 500.

## Schema

Six tables, created by `V1__create_schema.sql`. `users` holds the credentials and the active flag, and `trainees` and `trainers` each point at it with a unique foreign key. `trainings` links a trainee, a trainer and a type. `trainee2trainer` is the join table.

```
users             id, first_name, last_name, username (unique), password, is_active
trainees          id, user_id (unique FK), date_of_birth, address
trainers          id, user_id (unique FK), specialization_id (FK)
trainings         id, trainee_id (FK), trainer_id (FK), name, training_type_id (FK), date, duration
training_types    id, name (unique)
trainee2trainer   trainee_id (FK), trainer_id (FK)
```

Training types live in their own table because the same list is used by a trainer's specialization and a training's type. The table is read only from the application, the five rows come from `V2__seed_training_types.sql`.

A user is either a trainee or a trainer, never both. Every registration creates a new user, and no endpoint attaches a second profile to an existing one.

## Cascade delete

Deleting a trainee removes their user row, their trainings, and their rows in the join table. The trainers themselves are untouched, a remove cascade on the many to many would delete them too.

`TraineeDaoImplTest.deleteRemovesTrainingsAndUser` checks it at the DAO level, the integration test checks it over REST.

## Usernames and passwords

A username is `FirstName.LastName`, with a number appended if it is taken, so `John.Smith1`, then `John.Smith2`. The check covers trainees and trainers together. The column is 120 characters, enough for two 50 character names plus a suffix.

Passwords are 10 random characters from an alphabet that leaves out `I`, `O`, `l`, `0` and `1`, so they do not get misread. Generation is unchanged by the security module; `UserFactory` generates the password, hashes it for storage and hands the plain one back once for the registration response.

## Design

- **Facade**: `GymCrmFacade` is the single entry point the controllers use.
- **DAO**: persistence behind interfaces, `EntityManager` underneath.
- **DTO**: records for every request and response, built with static `from` methods.
- **Chain of Responsibility**: the servlet filter chain, encoding then transaction ID then request logging.
- **Chain of Responsibility**, again: the Spring Security filter chain, with the JWT filter as one more link.
- **Strategy**: `PasswordEncoder`, `UserDetailsService`, `AuthenticationEntryPoint`, `AccessDeniedHandler`, `LogoutHandler` and `LogoutSuccessHandler` are all Spring Security strategy interfaces with our implementations plugged in.
- **Factory**: `UserFactory` builds a new user with a generated username and a hashed password.
- **Template Method**: the filters extend `OncePerRequestFilter`, the error handler extends `ResponseEntityExceptionHandler`, and both required variable processors extend `RequiredDeployedVariableProcessor`.

## Testing

From a fresh `clean verify` of each service. Each pom has a JaCoCo check that fails the build below 80% line coverage. The workload-service suite runs MongoDB in Testcontainers, so it needs Docker running:

| Service | Tests | Line coverage | Branch coverage | Excluded from coverage |
|---|---|---|---|---|
| gym-crm | 629 | 100% (1123/1123) | 100% (170/170) | `GymCrmApplication` |
| workload-service | 325 | 100% (575/575) | 100% (124/124) | `WorkloadServiceApplication` |
| discovery-server | 2 | nothing to measure | nothing to measure | `DiscoveryServerApplication`, its only class |

Two tests worth knowing in the workload path:

- `WorkloadClientTest.appliesTheReadTimeout` points the client at a server that answers after the read
  timeout and asserts the call ends in the fallback after at least 3 and under 6 seconds, with the
  `HttpTimeoutException` in the WARN. It is the one test that really waits.
- `MongoPersistenceIntegrationTest.retriesConcurrentCreateAgainstRealIndex` lets a competitor create the
  trainer in the middle of an update, against the real unique index on a Testcontainers MongoDB, and asserts
  the change is retried on top of it (30 + 60 = 90 at version 1). `MongoTrainerWorkloadRepositoryTest`
  covers the retry budget, the 100-id window and a failed save with Mockito.

- DAO tests use `@DataJpaTest` against H2 and roll back after each test.
- Services and the facade are tested with Mockito, no Spring context.
- Controllers are tested with standalone MockMvc. The 403 cases need the real `@PreAuthorize` proxies, so they live in `MethodSecurityTest`, which uses `spring-security-test`.
- `SecurityIntegrationTest` goes through the full security chain over HTTP with real tokens: registration and login tokens, missing, garbage, expired and revoked tokens, someone else's data, the lockout with `Retry-After`, logout, CORS preflight, and that the stored password is a BCrypt hash.
- `JwtService`, `LoginAttemptService` and `TokenRevocationService` get an injected `Clock`, so expiry and the five minute lock are tested by moving time, not by sleeping.
- `SecurityConfigTest` checks that the JWT filter is not a bean, sits once in the chain, and runs before `LogoutFilter`.
- `WebContextTest` runs requests through the real application context, filters and database.
- `ProfileFetchIntegrationTest`, `ActuatorIntegrationTest` and `SecurityIntegrationTest` are the tests that commit. All three clear every table with `JdbcTestUtils` after each test, so no test depends on leftovers from another.
- `ProfileConfigTest` loads each `application-{profile}.yml` with `YamlPropertySourceLoader` and checks the URL, the pool size, `ddl-auto` and the Swagger flags, so a typo in a profile file fails the build instead of reaching an environment.
- `ActuatorIntegrationTest` reads `/actuator/health` and `/actuator/prometheus`, `ApiDocsIntegrationTest` reads the generated spec and the UI page, and `ApiDocsDisabledIntegrationTest` checks both return 404 with the two springdoc flags off.

The suite passes in normal order and in reverse alphabetical order (`-Dsurefire.runOrder=reversealphabetical`), so no test depends on another one running first.

## What the Spring Boot module added

- Converted to Spring Boot 4: auto configuration instead of the hand written contexts, the Maven plugin instead of `exec:java`.
- Four profiles, each with its own database, pool size, log levels and actuator exposure. stg and prod fail fast without `DB_URL`.
- Flyway migrations. The schema is versioned, Hibernate only validates it, and restarts no longer wipe data.
- Two custom health indicators: reference data present, and database latency under a configurable threshold.
- Prometheus metrics for registrations, logins, trainings and training duration.
- Swagger UI, off on stg and prod.

## What the Spring Security module added

- Spring Security on every endpoint except the two registrations and login. Basic auth and the hand written credentials resolver are gone.
- JWT bearer tokens from registration and `POST /auth/login`, signed with HS256 through jjwt, checked by our own filter in the security chain.
- BCrypt password hashes with a per hash salt.
- Brute force protection: three failed logins lock a username for five minutes, answered with 429 and `Retry-After`.
- Logout through Spring Security's logout support, with an in-memory deny list of revoked tokens.
- CORS per profile.
- `@PreAuthorize` self checks, and `POST /trainings` limited to its own trainee or trainer.
- Problem detail JSON for 401 and 403 from the security chain, tokens masked in the logs, and a `locked` outcome on the login metric.

## What the Microservices module added

- workload-service: monthly training minutes per trainer, JWT protected with read and write scopes, Swagger UI.
- discovery-server: a Eureka registry; gym-crm finds workload-service by name through it.
- Workload changes sent after commit, with a service token and the transaction id, behind a circuit breaker.
- `GET /trainers/{username}/workload/{year}/{month}`, with 503 when workload-service cannot answer.
- A Dockerfile per service and a compose file with health checks and ordered startup.

## What the Messaging module added

- ActiveMQ Artemis: workload writes go through the anycast queue `trainer.workload`, reads stay REST.
- A versioned event contract with a service token, the transaction id and per-trainer message groups.
- A transacted consumer with DLQ forwarding for invalid messages, broker redelivery for failures, and an
  idempotent consumer.
- Producer retry after commit, with client timeouts in the broker URL.
- Competing consumers with concurrency per profile; broker health in both services.
- An `artemis` compose service with mounted config, a log filter for tokens, a volume and a healthcheck.
  The test suites use an embedded broker.

## What the NoSQL module added

- MongoDB storage for workload-service: one document per trainer in `trainer_workloads`, behind the
  unchanged `TrainerWorkloadRepository`.
- Named indexes created at startup: unique `username`, and `firstName` + `lastName`.
- Optimistic locking with `@Version` and a `RetryTemplate`; concurrent creation handled as a conflict.
- Idempotency inside the document: the last 100 event ids, saved together with the change.
- Bean validation of the document before every save, and a Boolean status instead of the enum.
- A `mongo` compose service with a volume, a healthcheck, an init script for a least-privilege app user,
  and separate root and app env files. Tests run on Testcontainers MongoDB.

## Known limitations

A long MongoDB outage ends in the DLQ. Each delivery waits out the 2 s server selection timeout, and
the 3 deliveries span about 6 s, so an outage longer than that dead-letters every change made during it.
gym-crm still returned 200, and the changes wait in `DLQ.trainer.workload` until someone re-drives them.
With MongoDB back, re-drive them with the operations of the `DLQ.trainer.workload` queue MBean, which the
Artemis console at http://127.0.0.1:8161/console exposes in its JMX view. `browse()` lists the messages
with their `messageID`. `retryMessage(long)` moves one of them back to `trainer.workload`, and
`retryMessages()` moves all of them. The same operations through Jolokia, run inside the artemis
container so the broker credentials stay there. First the ids, with the transaction id of each message:

```bash
docker exec artemis sh -c 'curl -s -u "$ARTEMIS_USER:$ARTEMIS_PASSWORD" -H "Origin: http://localhost" -H "Content-Type: application/json" -d "{\"type\":\"exec\",\"mbean\":\"org.apache.activemq.artemis:broker=\\\"0.0.0.0\\\",component=addresses,address=\\\"DLQ.trainer.workload\\\",subcomponent=queues,routing-type=\\\"anycast\\\",queue=\\\"DLQ.trainer.workload\\\"\",\"operation\":\"browse()\"}" http://localhost:8161/console/jolokia/' | python3 -c 'import sys,json;[print(m["messageID"], (m.get("StringProperties") or {}).get("transactionId")) for m in json.load(sys.stdin)["value"]]'
```

Then one of them, by id:

```bash
ID=270936
docker exec artemis sh -c 'curl -s -u "$ARTEMIS_USER:$ARTEMIS_PASSWORD" -H "Origin: http://localhost" -H "Content-Type: application/json" -d "{\"type\":\"exec\",\"mbean\":\"org.apache.activemq.artemis:broker=\\\"0.0.0.0\\\",component=addresses,address=\\\"DLQ.trainer.workload\\\",subcomponent=queues,routing-type=\\\"anycast\\\",queue=\\\"DLQ.trainer.workload\\\"\",\"operation\":\"retryMessage(long)\",\"arguments\":[$1]}" http://localhost:8161/console/jolokia/' sh "$ID"
```

Checked live: the re-driven event was `APPLIED` on delivery 1, and the month went from 65 to 80. A
re-drive also works after the five-minute service token has expired, because the token is checked against
the time the message was sent (see Messaging, Token lifetime and queued messages). Checked live as well:
an event sent at 21:24:47 with a token valid until 21:29:47 was dead-lettered during a MongoDB outage,
re-driven at 21:31:32, and `APPLIED`. The broker had kept the original timestamp, and the month went from
80 to 115. Before that fix, such a re-drive was dead-lettered again as `UNAUTHENTICATED` (`Jwt expired at ...`).
`retryMessages()` also re-drives the invalid messages, which simply land in the DLQ again. Re-driving an
event that was already applied is harmless, because its id is in the document (within the 100-id window).

The Mongo passwords go into `MONGO_URI` unencoded, so they must be URL-safe. Use hex (`openssl rand -hex 24`).
A password with `@`, `:`, `/` or `%` breaks the URI. Percent-encoding the value in the env file would also
break the init script, which reads the same variable as the literal password.

There is no outbox. A change is published after commit, and if the broker stays unreachable through the 3 s retry budget it is lost: the ERROR line is the only record, and nothing reconciles the two stores. A transactional outbox would close that gap.

Idempotency covers the last 100 events per trainer. A duplicate older than that would be applied again. Redeliveries and producer retries arrive within seconds, far inside that window.

A message the broker dead-letters after 3 failed deliveries keeps all its properties, including the service token. The token has only `workload:write`, and the console that shows it is bound to 127.0.0.1. The broker's AMQ222149 log line, which would print it, is filtered out. Messages the listener forwards to the DLQ carry the original properties as well. Since tokens on messages are checked against the message's JMS timestamp, the five-minute lifetime no longer limits a copied token on the queue path. A client with broker credentials can set any timestamp through the Artemis core API, so it could send an old token with a matching old timestamp. The broker credentials are the real barrier, and the REST path still checks expiry against the current time.

There is no endpoint to delete a single training, by design. Trainings disappear only with their trainee, which is why every delete sends one `DELETE` per training.

The lockout counts and the revoked tokens live in memory. A restart clears both, and with two instances of the app each would keep its own. A shared store, for example Redis, would fix that.

Logout ends one token. Other tokens of the same user stay valid until they expire, and there is no refresh token: after an hour the user logs in again.

Changing the password or deactivating a user does not revoke tokens already issued. They keep working until they expire, up to an hour.

A database left over from the previous module holds plain text passwords, which never match a BCrypt hash. Drop and recreate it once.

A deactivated user can still log in and use the API, on purpose, as explained under Authentication.

`GET /training-types` needs credentials, because the PDF says every endpoint except registration does. A registration form that wants to show the type list first has no way to get it without an account.

Actuator and the API docs are open on local and dev. Anyone who can reach the port can read the metrics and the spec. On stg and prod the docs are off and actuator is cut to health and prometheus, but neither is authenticated, so they still rely on the network keeping them private.
