# Gym CRM

Hibernate module of the Gym CRM. Trainee and trainer profiles, their assignments to each other, and the trainings between them, stored in PostgreSQL through plain Hibernate. Still no web layer, everything goes through a facade called from Java.

## Stack

Java 21, Spring Framework 7.0.9 (`spring-context` and `spring-orm`, no Spring Boot), Hibernate 7.4.5 as the JPA provider with no Spring Data repositories, the DAOs use `EntityManager` directly. HikariCP for pooling, PostgreSQL 17, H2 for tests, SLF4J with Logback, Lombok, JUnit Jupiter, Mockito, JaCoCo.

## Getting it running

Clone it:

```bash
git clone https://github.com/GiorgiNikola/gym-crm.git
cd gym-crm
```

You need Java 21, Maven or the wrapper in the repo, and a PostgreSQL server on localhost:5432. Create the database first, the app creates the tables but not the database:

```sql
CREATE DATABASE gymcrm;
```

Credentials come from the environment. `application.properties` reads `${DB_USERNAME}` and `${DB_PASSWORD}` and has no default for either, so set both before you start anything. Miss one and startup fails on the unresolved placeholder:

PowerShell:

```powershell
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="yourpassword"
```

cmd:

```bash
set DB_USERNAME=postgres
set DB_PASSWORD=yourpassword
```

macOS and Linux:

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword
```

Either way they only last for that terminal session. `set` in PowerShell is an alias for `Set-Variable` and will not set an environment variable, which is an easy half hour to lose.

Then build and run:

```bash
./mvnw clean package
./mvnw exec:java
```

`hibernate.ddl-auto` is set to `create`, so every start drops the schema, recreates it from the entities and reinserts the five training types from `data.sql`. Nothing survives a restart. That is deliberate for a demo project, but do not point this at a database you care about.

Run the tests:

```bash
./mvnw clean test
```

Tests never touch PostgreSQL. `src/test/resources/application.properties` points them at in-memory H2 in PostgreSQL compatibility mode, so the suite runs on a machine with no database installed. JaCoCo is bound to the `test` phase, so the same command writes the coverage report to `target/site/jacoco/index.html`.

## Demo

There is a demo runner that walks the facade through 15 of its 18 operations. It only exists under the `demo` profile, so a plain run and the test suite never touch it.

```bash
./mvnw exec:java "-Dspring.profiles.active=demo"
```

Keep the quotes, PowerShell splits the argument on the dots without them.

It creates two trainers and a trainee, then a second John Smith who ends up as `John.Smith1`. It assigns trainers, books trainings, and shows the things that are supposed to fail: a Zumba class booked with a yoga trainer, activating an already active profile, and authenticating with a password that has just been changed. The last section deletes the trainee and reports the trainer's training count dropping to zero, which is the cascade doing its job. Every step logs a header line next to the services' own INFO lines. Passwords are never printed.

The three it skips are the trainer side mirrors of things it already does on the trainee: changing a trainer's password, and activating or deactivating a trainer.

The runner is `DemoRunner` in the `demo` package, marked `@Profile("demo")`. It fires off an `@EventListener` on `ContextRefreshedEvent`, so it runs once the context is fully built.

## How it's wired

Configuration is annotation based. `GymCrmApplication` carries `@Configuration` and `@ComponentScan`, and `main` builds an `AnnotationConfigApplicationContext` from it. Component scanning picks up `@Repository`, `@Service` and `@Component`.

There is no Spring Boot, so nothing autoconfigures the persistence layer. `config/PersistenceConfig` declares it by hand: a `HikariDataSource` from the `db.*` properties, a `LocalContainerEntityManagerFactoryBean` scanning `com.giorgi.gymcrm.model` with a `HibernateJpaVendorAdapter`, and a `JpaTransactionManager`. `@EnableTransactionManagement` is what makes `@Transactional` do anything.

Two things there are easy to miss. The `PropertySourcesPlaceholderConfigurer` has to be declared, and has to be `static`, or the `${...}` placeholders never resolve. And `data.sql` runs through a `DataSourceInitializer` marked `@DependsOn("entityManagerFactory")`, because the entity manager factory is what creates the tables and the insert has to wait for them.

Injection is setter based in the services and `UsernameResolver`, the same split the previous module used. `GymCrmFacade` takes its three services through the constructor, which is what the original task asks for. The DAOs get their `EntityManager` through `@PersistenceContext` field injection, which is the standard way to do it: the injected instance is a proxy that resolves to the transaction bound `EntityManager` at call time, so one DAO bean is safe across concurrent transactions.

`DemoRunner` also takes the facade through its constructor, since it is a runner rather than a service.

Transactions live on the service layer, never on the DAOs and never on the facade. Writes get `@Transactional`, reads get `@Transactional(readOnly = true)`. A service method is the unit of work, so `updateTrainers` resolving five trainer usernames and replacing the set either happens completely or not at all.

The one service without a `@Transactional` of its own is `UsernameResolver`. It runs inside whichever transaction called it, which is fine because the only callers are the two `createProfile` methods, but it is not safe to call on its own.

## Schema

Six tables. `users` holds the credentials and the active flag. `trainees` and `trainers` each have their own primary key and a unique foreign key to `users`, which is the parent child one to one the task describes. `trainings` points at a trainee, a trainer and a training type. `trainee2trainer` is the many to many join table.

```
users             id, first_name, last_name, username (unique), password, is_active
trainees          id, user_id (unique FK), date_of_birth, address
trainers          id, user_id (unique FK), specialization_id (FK)
trainings         id, trainee_id (FK), trainer_id (FK), name, training_type_id (FK), date, duration
training_types    id, name (unique)
trainee2trainer   trainee_id (FK), trainer_id (FK), composite PK
```

`TrainingType` is mapped `@Immutable` with getters only, no setters and no builder. The five rows come from `data.sql` and nothing in the application can write to that table, which is what the task asks for.

On why training and training type are separate tables: the type is a fixed list shared by two different things, a trainer's specialization and a training's type, so keeping it in one table gives both a foreign key instead of a free text string that can be typed wrong. Adding a sixth type becomes a data change rather than a schema or code change, and renaming one updates every row that references it. The alternative, an enum column on each table, would duplicate the list in two places and let the two drift apart.

Every `@ManyToOne` and `@OneToOne` is explicitly `LAZY`, since the JPA default for to-one associations is eager and that pulls in rows nobody asked for. The three `findByUsername` queries then `join fetch` what a caller actually reads, so a trainee comes back with its user loaded and a trainer with its user and specialization loaded.

The two filtered list queries are the exception. They join to filter but do not fetch, so a `Training` comes back with its trainee, trainer and type still proxies. Only the training's own columns are safe to read once the service method has returned.

## Cascade delete

Deleting a trainee is a hard delete that takes their trainings with it, per the task spec. Three things make that work:

`Trainee.user` is `cascade = ALL`, so removing the trainee removes its `users` row too. `Trainee.trainings` is `cascade = REMOVE` with `orphanRemoval = true`, so the trainings go. The owning `@ManyToMany` on `Trainee.trainers` deletes its own join table rows, and because the cascade stops there the trainers themselves are untouched. Putting a remove cascade on a many to many would delete the trainers, which is the obvious trap in this task.

`TraineeDaoImplTest.deleteRemovesTrainingsAndUser` asserts all of it: trainee gone, user gone, both trainings gone, join row gone, trainer and the trainer's user still there.

## Authentication

Every operation except the two profile creations authenticates first. `AuthenticationService.authenticate` looks the user up by username, compares the password, and throws `AuthenticationException` if either the user is missing or the password does not match. Both cases give the same message on purpose, so the error does not reveal which usernames exist.

Credentials are passed as the first two arguments of every operation except the two creations. There is no session and no security context yet, so this is the honest version of what the task asks for at this stage. It goes away when Spring Security arrives in a later module.

## Validation and errors

Creating or updating a profile needs a non-blank first and last name. A trainer also needs a specialization that exists in `training_types`. A training needs a non-blank name, a known type, a date, and a duration above zero. The blank checks all go through `Validations.requireText`.

Three exceptions come out of the facade. `IllegalArgumentException` for bad input, which includes a training type that does not match the trainer's specialization and activating a profile that is already active. `ProfileNotFoundException` when a username does not resolve to a trainee or trainer. `AuthenticationException` when the credentials do not match. Nothing catches any of them, there is no web layer to turn them into responses yet.

## Facade

`GymCrmFacade` is the only entry point, it just forwards to the three services.

```
Trainee        createTraineeProfile(firstName, lastName, dateOfBirth, address)
Trainee        selectTraineeProfile(username, password)
void           changeTraineePassword(username, password, newPassword)
Trainee        updateTraineeProfile(username, password, firstName, lastName, dateOfBirth, address)
void           activateTrainee(username, password)
void           deactivateTrainee(username, password)
void           deleteTraineeProfile(username, password)
Trainee        updateTraineeTrainers(username, password, trainerUsernames)
List<Trainer>  getUnassignedTrainers(username, password)

Trainer        createTrainerProfile(firstName, lastName, specializationName)
Trainer        selectTrainerProfile(username, password)
void           changeTrainerPassword(username, password, newPassword)
Trainer        updateTrainerProfile(username, password, firstName, lastName, specializationName)
void           activateTrainer(username, password)
void           deactivateTrainer(username, password)

Training       addTraining(username, password, traineeUsername, trainerUsername, name, typeName, date, duration)
List<Training> getTraineeTrainings(username, password, fromDate, toDate, trainerName, typeName)
List<Training> getTrainerTrainings(username, password, fromDate, toDate, traineeName)
```

There is no delete for a trainer or a training, which matches the task spec.

Activate and deactivate are separate operations and neither is idempotent: activating an already active profile throws rather than quietly doing nothing. That is the reading of note 6 in the task.

`updateTraineeTrainers` replaces the whole set with what you pass. An empty set clears every assignment.

## Filtered training lists

The two list operations take four and three optional filters. They are built with the Criteria API rather than JPQL, because a query whose shape depends on which arguments are present would otherwise mean either sixteen query strings or gluing one together at runtime. Each filter that is present adds a predicate to a list, and only those predicates reach the generated SQL. Results are ordered by date.

## Usernames and passwords

A username is `FirstName.LastName`. If it is taken, a serial number gets appended, so `John.Smith1`, then `John.Smith2`. The check runs against the `users` table, which both trainees and trainers write to, so a trainee and a trainer can never share a username. The suffix always goes on the base name, it never stacks onto the previous candidate.

Passwords are 10 random characters drawn from `ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789`, generated with `SecureRandom`. That alphabet leaves out `I`, `O`, `l`, `0` and `1` so a password does not get misread. A new one is generated on every profile creation.

Updating a profile does not recalculate the username. A name change leaves the existing username in place, so an update cannot produce a collision.

## Design

A DAO per entity, `@Repository`, `EntityManager` based. Five of them, since `UserDao` backs the username uniqueness check and `TrainingTypeDao` looks a type up by name. Facade in front of the services. Builder through Lombok on the entities, with `@NoArgsConstructor` alongside it because JPA instantiates entities reflectively and `@Builder` would otherwise suppress the default constructor.

No entity declares `equals`, `hashCode` or `toString`. Lombok's generated versions touch every field, which triggers lazy loading on associations and can recurse between `Trainee` and `Trainer`. Identity semantics are correct inside a single persistence context, which is the only place entities live here.

## Logging

SLF4J through Lombok's `@Slf4j`, with Logback behind it. `logback.xml` sets `com.giorgi.gymcrm` and `org.hibernate.SQL` to DEBUG and everything else to INFO. `logback-test.xml` quiets both the SQL and the root logger for test runs.

INFO is for state changes that actually happened: profile created, profile updated, password changed, profile deleted, trainer list replaced, activation flipped, training added. Each one is logged after the DAO call returns, so a failed write never leaves a success line behind.

WARN is for a failed authentication attempt. The message names the username, never the password.

DEBUG is for lookups and internals: the id of a persisted row, the username a successful authentication resolved to, the username `UsernameResolver` settled on, and every taken candidate it had to skip.

A failed authentication is the only failure that gets logged. Validation failures, an unknown username and an unknown training type are thrown without a log line, because the caller gets the exception and nothing here can do anything useful about it. There is no ERROR level anywhere, since none of these are the application failing at something. No log statement passes a password, and since no entity has a Lombok `toString`, none can leak one indirectly either. Hibernate's SQL logging prints statements with `?` placeholders, so bound values including passwords do not appear.

## Testing

119 tests. Line coverage is above 80%, with the demo runner and the application entry point excluded from the report since neither contains logic worth testing. Open the JaCoCo report for the current numbers.

DAO tests run against H2 with `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration` and `@Transactional`, so each test rolls back and the next one starts clean. They are the ones that prove the mappings and the queries: the cascade delete, the fetch joins actually initializing their associations, `findNotAssignedToTrainee` excluding both assigned and inactive trainers, and the list filters one at a time and several at once. The one case they do not cover is a filter passed as a blank string instead of null.

Services, `UsernameResolver` and the facade are tested with Mockito and no Spring context. The facade tests are pure delegation checks, they catch wrong wiring and swapped arguments and nothing more.

`GymCrmApplicationTests` boots the real context once as a smoke test. The test properties set Hibernate's SQL logging to INFO, so a full run stays readable.

## Known limitations

Passwords are stored and compared in plain text. The task specifies password matching at this stage, hashing arrives with Spring Security.

Credentials are passed into every operation as arguments. There is no session, so each call authenticates from scratch.

Authentication is not authorization. `addTraining` checks that the caller is a real user, but does not check that the caller is the trainee or the trainer involved, so any authenticated user can book a training between any two people.

The trainer name and trainee name filters on the training lists match the first name only, and match on a substring. Searching by last name returns nothing.

Changing a trainer's specialization does not touch their existing trainings. A training's type is checked against the specialization when it is created, so after a specialization change a trainer can have older trainings whose type no longer matches.

Username uniqueness is a check followed by an insert, with no lock in between. Two profiles created at the same time with the same name can both see the username as free. The unique constraint on `users.username` catches it, so the result is a constraint violation rather than a duplicate.

The filtered training lists hand back entities whose trainee, trainer and type are still proxies, and the transaction is already over by the time the caller has the list. Reading those associations from outside throws `LazyInitializationException`.

`ddl-auto` is `create`, so every application start wipes the database and reseeds the training types. Fine for a demo project, it should not survive into anything real.

Nothing is guarded for concurrent use beyond what the database itself provides.