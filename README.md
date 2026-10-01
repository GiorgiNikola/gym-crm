# Gym CRM

Hibernate module of the Gym CRM. Trainee and trainer profiles, the assignments between them, and the trainings they do, stored in PostgreSQL. No web layer yet, everything goes through a facade you call from Java.

## Stack

Java 21, Spring Framework 7 (no Spring Boot), Hibernate 7 as the JPA provider, PostgreSQL 17, H2 for tests, HikariCP, Lombok, JUnit 5, Mockito, JaCoCo.

No Spring Data repositories. The DAOs use `EntityManager` directly, since the point of this module is Hibernate.

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

That is PowerShell, use `export` on macOS and Linux.

```bash
./mvnw clean package
./mvnw exec:java
```

Every start drops the schema and rebuilds it, so nothing survives a restart. Do not point this at a database you care about.

Tests run against in-memory H2, so they work on a machine with no database installed:

```bash
./mvnw clean test
```

That also writes the coverage report to `target/site/jacoco/index.html`.

## Demo

There is a runner that walks the facade through most of the operations. It only exists under the `demo` profile, so normal runs and tests never touch it.

```bash
./mvnw exec:java "-Dspring.profiles.active=demo"
```

Keep the quotes in PowerShell.

It creates two trainers and a trainee, then a second John Smith who becomes `John.Smith1`. It assigns trainers, books trainings, and shows the things that should fail: a Zumba class with a yoga trainer, activating an already active profile, logging in with a password that was just changed. Last it deletes the trainee and shows the trainer's training count drop to zero, which is the cascade working. Passwords are never printed.

## How it's wired

There is no Spring Boot here, so the persistence layer is configured by hand in `PersistenceConfig`: a Hikari data source, an entity manager factory, and a transaction manager. `GymCrmApplication` builds the context and component scanning finds everything else.

Injection is setter based in the services, constructor based in the facade. The DAOs get their `EntityManager` through `@PersistenceContext`.

Transactions sit on the services, never on the DAOs or the facade. One service method is one unit of work, so something like replacing a trainee's whole trainer list either happens completely or not at all.

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

Training types live in their own table because the same list is used by two things, a trainer's specialization and a training's type. Both get a foreign key instead of a free text string, and adding a sixth type becomes a data change rather than a code change. The table is read only from the application, the five rows come from `data.sql`.

## Cascade delete

Deleting a trainee removes their user row, their trainings, and their rows in the join table. The trainers themselves are untouched, which is the thing to get right: a remove cascade on the many to many would delete them too.

`TraineeDaoImplTest.deleteRemovesTrainingsAndUser` checks all of it.

## Authentication

Everything except creating a profile needs a username and password, checked at the start of the operation. A wrong password and an unknown username give the same error, so it does not reveal which usernames exist.

Credentials are passed in as the first two arguments of every operation. There is no session yet, that comes with Spring Security later.

## Facade

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

No delete for a trainer or a training, which matches the task spec.

Activate and deactivate are separate and neither is idempotent: activating an already active profile throws instead of quietly doing nothing.

`updateTraineeTrainers` replaces the whole list with what you pass. An empty set clears it.

The two training lists take optional filters. They are built with the Criteria API, because a query whose shape depends on which filters were passed would otherwise mean sixteen versions of the same query.

## Usernames and passwords

A username is `FirstName.LastName`, with a number appended if it is taken, so `John.Smith1`, then `John.Smith2`. The check covers trainees and trainers together, so the two can never share a username. The number always goes on the base name, it does not stack onto the previous attempt.

Passwords are 10 random characters from an alphabet that leaves out `I`, `O`, `l`, `0` and `1`, so they do not get misread. A new one is generated for every profile.

Updating a profile does not change the username, so a name change cannot create a collision.

## Errors

Three things come out of the facade. `IllegalArgumentException` for bad input, which includes a training type that does not match the trainer's specialization. `ProfileNotFoundException` when a username does not exist. `AuthenticationException` when the credentials are wrong. Nothing catches them, there is no web layer to turn them into responses yet.

## Logging

INFO for things that happened: profile created, updated, deleted, password changed, training added, activation flipped. Each is logged after the write succeeds, so a failed one never leaves a success line.

WARN for a failed login, naming the username only.

DEBUG for lookups and internals, including every taken username the resolver had to skip.

No log line anywhere contains a password.

## Testing

119 tests, above 80% line coverage with the demo runner excluded.

The DAO tests run against H2 and roll back after each test. They are the ones that matter, they prove the mappings and the queries: the cascade delete, the fetch joins, the filters one at a time and several at once. Services and the facade are tested with Mockito and no Spring context.

## Known limitations

Passwords are stored and compared in plain text. The task asks for password matching at this stage, hashing comes with Spring Security.

Being logged in is not the same as being allowed. `addTraining` checks you are a real user but not that you are one of the two people involved, so anyone can book a training between any two users.

The name filters on the training lists only match first names.

Changing a trainer's specialization leaves their old trainings alone, so a trainer can end up with trainings whose type no longer matches what they teach.

Two people registering the same name at the exact same moment can both see the username as free. The database constraint catches it, so you get an error rather than a duplicate.

The filtered training lists return trainings whose trainee, trainer and type are not loaded. Reading those after the call throws. Only the training's own fields are safe.

Every start wipes the database. Fine here, not fine anywhere real.

Nothing is thread safe beyond what the database itself provides.