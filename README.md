# Gym CRM

Trainees and trainers register, log in, manage their profiles, pick trainers and log trainings, all over HTTP. Data is stored in PostgreSQL. Spring Boot application with four environment profiles, Flyway migrations, actuator health and Prometheus metrics, and Swagger UI.

## Stack

Java 21 and Spring Boot 4.0.8. The versions Boot manages, from `./mvnw dependency:tree`:

| Part | Version |
|---|---|
| Spring Framework | 7.0.9 |
| Hibernate ORM | 7.2.24.Final |
| Jackson (`tools.jackson`) | 3.1.5 |
| Tomcat (embedded) | 11.0.26 |
| Flyway | 11.14.1 |
| Micrometer | 1.16.7 |
| springdoc OpenAPI | 3.0.3 |
| HikariCP | 7.0.2 |
| PostgreSQL driver | 42.7.13 |
| H2 (tests only) | 2.4.240 |

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

`DB_USERNAME` and `DB_PASSWORD` are required everywhere.

stg and prod have no fallback URL on purpose, and they refuse to start without one:

```
java.lang.IllegalStateException: The prod profile has no default database url: set the DB_URL
environment variable to the jdbc url of the prod database
```

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

The spec is generated from the Swagger annotations on the controllers. It declares `basicAuth` as an HTTP basic security scheme, so the UI shows an Authorize button. Fill it in once and every secured endpoint sends the header. The two registration endpoints carry no security block, so they show no padlock.

Both are off on stg and prod, through `springdoc.api-docs.enabled: false` and `springdoc.swagger-ui.enabled: false`. The spec and the UI return 404 there.

## Actuator

Exposed per profile as in the table above. Nothing is behind Basic auth: the authentication in this app is a per-endpoint argument resolver on the controllers, and actuator endpoints are not controllers. On stg and prod the exposure is cut to health and prometheus, and health shows which components exist without their details.

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
| `gymcrm.logins` | counter | `outcome` = success or failure |
| `gymcrm.trainings.added` | counter | none |
| `gymcrm.training.duration` | distribution summary, minutes | none |

All of them are registered at startup, so they appear in a scrape at 0 before the first event instead of being missing.

```bash
curl -s http://localhost:8080/actuator/prometheus | grep gymcrm_
```

```
gymcrm_logins_total{application="gym-crm",outcome="failure"} 0.0
gymcrm_logins_total{application="gym-crm",outcome="success"} 0.0
gymcrm_registrations_total{application="gym-crm",role="trainee"} 1.0
gymcrm_registrations_total{application="gym-crm",role="trainer"} 0.0
gymcrm_training_duration_minutes_count{application="gym-crm"} 0
gymcrm_training_duration_minutes_sum{application="gym-crm"} 0.0
gymcrm_trainings_added_total{application="gym-crm"} 0.0
```

Two things to know when reading the login counter. It counts authentications, not calls to `/auth/login`, so every secured endpoint adds to it. And a blank or malformed `Authorization` header is not counted as a failure, because it never reaches the password check, it is rejected as a bad request first. Only a wrong username or password counts as a failure.

No usernames in tags. One time series per user would be unbounded.

## Trying it

`postman/gym-crm.postman_collection.json` holds 63 requests with assertions on every one. Import it, start the app, and run the whole collection with the Collection Runner. It registers fresh users on every run, so you can run it again without restarting.

The requests use Basic auth from collection variables, and the registration requests save the generated credentials into those variables. You never copy a password by hand.

## Endpoints

| PDF | Method | Path | Auth | Success |
|---|---|---|---|---|
| 1 | POST | `/trainees` | none | 201 |
| 2 | POST | `/trainers` | none | 201 |
| 3 | GET | `/auth/login` | Basic | 200 |
| 4 | PUT | `/users/{username}/password` | old password in body | 200 |
| 5 | GET | `/trainees/{username}` | Basic, self | 200 |
| 6 | PUT | `/trainees/{username}` | Basic, self | 200 |
| 7 | DELETE | `/trainees/{username}` | Basic, self | 200 |
| 8 | GET | `/trainers/{username}` | Basic, self | 200 |
| 9 | PUT | `/trainers/{username}` | Basic, self | 200 |
| 10 | GET | `/trainees/{username}/unassigned-trainers` | Basic, self | 200 |
| 11 | PUT | `/trainees/{username}/trainers` | Basic, self | 200 |
| 12 | GET | `/trainees/{username}/trainings` | Basic, self | 200 |
| 13 | GET | `/trainers/{username}/trainings` | Basic, self | 200 |
| 14 | POST | `/trainings` | Basic | 200 |
| 15, 16 | PATCH | `/users/{username}/status` | Basic, self | 200 |
| 17 | GET | `/training-types` | Basic | 200 |

The PDF lists activation for trainees and trainers as two endpoints. The active flag lives on the `users` table, and usernames are unique across both, so it is one endpoint here and one method in the code.

URLs use plural nouns and the username as the identifier. Usernames never change, so a URL never goes stale. Things that only touch the `users` table sit under `/users`, profiles under `/trainees` and `/trainers`.

### Status codes worth knowing

Registration returns 201 with a `Location` header pointing at the new profile. DELETE and POST `/trainings` return 200 with an empty body, because the PDF says 200 for both.

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

Every request except the two registrations carries HTTP Basic credentials. A resolver parses the `Authorization` header into a `Credentials` object and the services check it against the database.

A missing, malformed or blank header gives 401 with `WWW-Authenticate: Basic realm="gym-crm"`. A wrong password and an unknown username give the same 401, so it does not reveal which usernames exist.

Endpoints with `{username}` in the path only act on yourself. If the path does not match the username in the credentials, the answer is 403. Change password is the exception: the task's request has the username and old password in it, so that endpoint authenticates with those and ignores the header.

## Errors

Every error is an RFC 9457 problem detail with content type `application/problem+json`. This is a real response to `PATCH /users/Err.Case/status` with an empty body:

```json
{"detail":"Validation failed","instance":"/users/Err.Case/status","status":400,"title":"Bad Request","errors":{"isActive":"must not be null"}}
```

There is no `type` field. Spring leaves it out while it is the default `about:blank`.

| Status | When |
|---|---|
| 400 | Validation failed (with an `errors` map), malformed JSON, bad input like an unknown training type or a from date after the to date |
| 401 | Missing or wrong credentials |
| 403 | Path username does not match the credentials |
| 404 | Profile or trainer not found, or an unknown URL |
| 409 | Status already set, or a username taken by a parallel registration |
| 500 | Anything unexpected. The detail is a fixed "Unexpected error", never the exception message |

Unknown URLs return the same JSON shape, not Tomcat's HTML error page.

## Logging

Each request gets a transaction ID. If the request carries an `X-Transaction-Id` header with up to 64 letters, digits and dashes, that one is reused, otherwise a UUID is generated. Anything else in the header is replaced, so a caller cannot inject fake log lines. The ID goes into the MDC, so every log line of the request shows it, and it comes back in the response header. It is removed when the request ends, because Tomcat reuses threads.

One summary line per request: method, URL, status and time taken. INFO below 400, WARN for 4xx with the error body, ERROR for 5xx. Request and response bodies are logged at DEBUG.

Actuator and API docs requests are not logged at all. Prometheus scrapes every few seconds and the docs UI pulls the spec on every page load, and those lines would bury the real traffic. The skipped prefixes are `/actuator/`, `/swagger-ui.html`, `/swagger-ui/` and `/v3/api-docs`, kept in one list in `RequestLoggingFilter`. The transaction ID filter still runs for everything.

Any JSON field whose name ends in `password` is masked before it is logged, including the generated password in a registration response. Headers are never logged, so the `Authorization` header never reaches the log.

A failed request is logged once. Only the 500 handler adds a stack trace, because only a 500 is a bug.

`logback-spring.xml` holds the console pattern, including the transaction ID. Levels are not in it, they live in the profile YAMLs under `logging.level`, so there is one place to look.

## How it's wired

`GymCrmApplication` is a plain `@SpringBootApplication`, plus `@ConfigurationPropertiesScan` so the `@ConfigurationProperties` records bind without being listed one by one.

- `FilterConfig` registers the two filters. Boot's character encoding filter runs first at `HIGHEST_PRECEDENCE`, then the transaction ID filter at `+1`, then request logging at `+2`. The order matters: the ID has to be on the MDC before the request log writes its line. Boot slots its own filters in between, so `FilterConfigTest` asserts the relative order rather than fixed positions.
- `WebConfig` is a `WebMvcConfigurer` that adds the Basic credentials resolver, and carries the `@SecurityScheme` annotation the spec is generated from.
- `RequiredDatabaseUrlProcessor` is an `EnvironmentPostProcessor` registered in `META-INF/spring.factories`. It stops stg and prod before any bean is created when `DB_URL` is missing.
- `StartupInfoLogger` writes one line on startup with the active profiles and the JDBC URL. The username and password are separate properties and never reach the log.

Injection is setter based in the services, constructor based in the facade and controllers. Transactions sit on the services, one service method is one unit of work.

Controllers only talk to the facade and only return DTOs, never entities.

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

Passwords are 10 random characters from an alphabet that leaves out `I`, `O`, `l`, `0` and `1`, so they do not get misread.

## Design

- **Facade**: `GymCrmFacade` is the single entry point the controllers use.
- **DAO**: persistence behind interfaces, `EntityManager` underneath.
- **DTO**: records for every request and response, built with static `from` methods.
- **Chain of Responsibility**: the servlet filter chain, encoding then transaction ID then request logging.
- **Strategy**: the Basic credentials resolver plugs into Spring MVC's argument resolution.
- **Template Method**: the filters extend `OncePerRequestFilter`, the error handler extends `ResponseEntityExceptionHandler`.

## Testing

358 tests, 100% line and branch coverage, with only `GymCrmApplication` excluded.

- DAO tests use `@DataJpaTest` against H2 and roll back after each test.
- Services and the facade are tested with Mockito, no Spring context.
- Controllers are tested with standalone MockMvc.
- `WebContextTest` runs requests through the real application context, filters and database.
- `ProfileFetchIntegrationTest` and `ActuatorIntegrationTest` are the tests that commit. Both clear every table with `JdbcTestUtils` after each test, so no test depends on leftovers from another.
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

## Known limitations

Passwords are stored and compared in plain text. The task asks for password matching at this stage, hashing comes with Spring Security.

Being logged in is not the same as being allowed. The self check covers every endpoint with a username in the path, but POST `/trainings` takes both people from the body, so any user can log a training between any trainee and trainer.

A deactivated user can still log in and use the API. The task does not say what deactivation should block.

`GET /training-types` needs credentials, because the PDF says every endpoint except registration does. A registration form that wants to show the type list first has no way to get it without an account.

Actuator and the API docs are open on local and dev. Anyone who can reach the port can read the metrics and the spec. On stg and prod the docs are off and actuator is cut to health and prometheus, but neither is authenticated, so they still rely on the network keeping them private.
