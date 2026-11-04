# Gym CRM

REST module of the Gym CRM. Trainees and trainers register, log in, manage their profiles, pick trainers and log trainings, all over HTTP. Data is stored in PostgreSQL.

## Stack

Java 21, Spring Framework 7 with Spring MVC (no Spring Boot), embedded Tomcat 11, Jackson 3, Hibernate 7, Hibernate Validator, PostgreSQL 17, H2 for tests, HikariCP, Lombok, JUnit 5, Mockito, JaCoCo.

The API is documented with Swagger annotations on the controllers. There is no Swagger UI: springdoc, the usual way to serve one, depends on Spring Boot, and this module is not allowed to use it.

## Getting it running

You need Java 21 and a PostgreSQL server on localhost:5432. Create the database first, the app creates the tables but not the database:

```sql
CREATE DATABASE gymcrm;
```

Credentials come from the environment, there are no defaults:

```powershell
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="yourpassword"
```

That is PowerShell, use `export` on macOS and Linux. Then:

```bash
./mvnw compile exec:java
```

The server starts on `http://localhost:8080` and keeps running until you stop it. `exec:java` does not compile on its own, so keep `compile` in front of it.

Every start drops the schema and rebuilds it, so nothing survives a restart. Do not point this at a database you care about.

Tests run against in-memory H2, so they work on a machine with no database installed:

```bash
./mvnw clean verify
```

That also writes the coverage report to `target/site/jacoco/index.html`.

## Trying it

`postman/gym-crm.postman_collection.json` holds 61 requests with assertions on every one. Import it, start the app, and run the whole collection with the Collection Runner. It registers fresh users on every run, so you can run it again without restarting.

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

Every request except the two registrations carries HTTP Basic credentials. A resolver parses the `Authorization` header into a `Credentials` object and the services check it against the database, the same check the previous module did.

A missing, malformed or blank header gives 401 with `WWW-Authenticate: Basic realm="gym-crm"`. A wrong password and an unknown username give the same 401, so it does not reveal which usernames exist.

Endpoints with `{username}` in the path only act on yourself. If the path does not match the username in the credentials, the answer is 403. Change password is the exception: the task's request has the username and old password in it, so that endpoint authenticates with those and ignores the header.

## Errors

Every error is an RFC 9457 problem detail with content type `application/problem+json`:

```json
{"type":"about:blank","title":"Bad Request","status":400,"detail":"Validation failed","instance":"/users/John.Smith/status","errors":{"isActive":"must not be null"}}
```

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

Any JSON field whose name ends in `password` is masked before it is logged, including the generated password in a registration response. Headers are never logged, so the `Authorization` header never reaches the log.

A failed request is logged once. Only the 500 handler adds a stack trace, because only a 500 is a bug.

## How it's wired

There is no Spring Boot, so everything Boot would do is in `GymCrmApplication.main`:

1. A root context from `RootConfig`: persistence, DAOs, services, facade.
2. A web context from `WebConfig`: controllers, the error handler, the Basic credentials resolver. Its parent is the root context, so controllers can see the facade, but nothing in the root can see a controller.
3. An embedded Tomcat on port 8080, with the two filters (transaction ID first, then request logging) and a `DispatcherServlet` in front of the web context.

The split exists for the tests. DAO tests load only `RootConfig` and never start the web layer.

Tomcat's class loader is given the application's class loader as its parent. Without that, `mvn exec:java` loads the app in a separate class loader that Tomcat's request threads cannot see, component scanning finds nothing, and every request returns 404.

Injection is setter based in the services, constructor based in the facade and controllers. Transactions sit on the services, one service method is one unit of work.

Controllers only talk to the facade and only return DTOs, never entities.

## Fetching

Profile endpoints return the trainee with their trainers, or the trainer with their trainees. `findProfileByUsername` loads the whole graph in one query with `join fetch`, so a profile with five trainers is one query instead of eleven.

The collection side uses `left join fetch`. With a plain join, a trainee with no trainers would match no rows and come back as "not found".

DTOs are built in the controller, after the service transaction has closed, so anything not fetched would throw `LazyInitializationException`. `ProfileFetchIntegrationTest` runs without a transaction on purpose to catch exactly that. Removing one `join fetch` from the profile query makes it fail with a 500.

## Schema

Six tables. `users` holds the credentials and the active flag, and `trainees` and `trainers` each point at it with a unique foreign key. `trainings` links a trainee, a trainer and a type. `trainee2trainer` is the join table.

```
users             id, first_name, last_name, username (unique), password, is_active
trainees          id, user_id (unique FK), date_of_birth, address
trainers          id, user_id (unique FK), specialization_id (FK)
trainings         id, trainee_id (FK), trainer_id (FK), name, training_type_id (FK), date, duration
training_types    id, name (unique)
trainee2trainer   trainee_id (FK), trainer_id (FK)
```

Training types live in their own table because the same list is used by a trainer's specialization and a training's type. The table is read only from the application, the five rows come from `data.sql`.

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
- **Chain of Responsibility**: the servlet filter chain, transaction ID then request logging.
- **Strategy**: the Basic credentials resolver plugs into Spring MVC's argument resolution.
- **Template Method**: the filters extend `OncePerRequestFilter`, the error handler extends `ResponseEntityExceptionHandler`.

## Testing

272 tests, 100% line and branch coverage, with only `GymCrmApplication` excluded.

- DAO tests run against H2 and roll back after each test.
- Services and the facade are tested with Mockito, no Spring context.
- Controllers are tested with standalone MockMvc.
- `WebContextTest` runs requests through the real `WebConfig`, filters and database.
- `ProfileFetchIntegrationTest` is the one test that commits. It cleans every table after each test.

The suite passes in normal order and in reverse alphabetical order (`-Dsurefire.runOrder=reversealphabetical`), so no test depends on another one running first.

## Closed in this module

- Trainer specialization changes are gone: specialization is read only, so a trainer's trainings always match what they teach.
- The name filters match last names and full names, not only first names.
- A username taken by a parallel registration returns 409 instead of an unhandled database error.

## Known limitations

Passwords are stored and compared in plain text. The task asks for password matching at this stage, hashing comes with Spring Security.

Being logged in is not the same as being allowed. The self check covers every endpoint with a username in the path, but POST `/trainings` takes both people from the body, so any user can log a training between any trainee and trainer.

A deactivated user can still log in and use the API. The task does not say what deactivation should block.

`GET /training-types` needs credentials, because the PDF says every endpoint except registration does. A registration form that wants to show the type list first has no way to get it without an account.

Every start wipes the database. Fine here, not fine anywhere real.