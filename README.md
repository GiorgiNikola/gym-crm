# Gym CRM

Spring Core module of the Gym CRM. Everything lives in memory, there's no web layer and no database.

## Stack

Java 21, Spring Boot 4.1.1 (Spring Core only, no web starter), Lombok 1.18.46, JUnit Jupiter 6.0.3, Mockito 5.23.0, JaCoCo 0.8.13.

## Getting it running

Clone it:

```bash
git clone -b spring-core https://github.com/GiorgiNikola/gym-crm.git
cd gym-crm
```

You need Java 21 and Maven, or just use the wrapper that's already in the repo.

```bash
./mvnw clean package
```

Run the tests:

```bash
./mvnw clean test
```

JaCoCo is bound to the `test` phase, so that same command writes the coverage report. Open `target/site/jacoco/index.html` to read it.

You can start the app too:

```bash
./mvnw spring-boot:run
```

Be aware there's nothing to talk to. It boots the Spring context, `StorageInitializer` fills the three storage maps from the CSV files and logs how many rows each one got, and then the process exits because there's no web server or anything else keeping it alive. If you want to actually see it do something, run the demo below.

## Demo

There's a demo runner that goes through the facade against the seeded data. It only exists under the `demo` profile, so a plain `spring-boot:run` and the tests never touch it.

```bash
./mvnw spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Keep the quotes, PowerShell splits the argument on the dots without them.

It looks up a seeded trainee, then creates a second John Smith, who ends up as `John.Smith1`. Then a trainer called Ana Kapanadze, who gets `Ana.Kapanadze1` because a trainee already owns the base name. It books a yoga training between the two, tries to change John's username through an update and shows it stays put, then creates a throwaway trainee and deletes it. Each step logs a line starting with `Demo:` next to the services' own INFO lines. Passwords only show up as their length. The process exits when it's done, and since storage is in memory, the next run starts from the CSV seed again.

The demo is `GymCrmDemo` in the `demo` package, a `CommandLineRunner` marked `@Profile("demo")`. It gets the facade through a setter like everything else.

## How it's wired

Configuration is annotation based. `StorageConfig` is a `@Configuration` class, and everything else is picked up by component scanning through `@Component` and `@Service`.

Each storage map is its own bean. `StorageConfig` declares three separate `@Bean` methods, `traineeStorage()`, `trainerStorage()` and `trainingStorage()`, each returning a plain `HashMap`. They're distinct beans rather than one shared structure, so each entity type can be listed on its own.

Injection is setter based almost everywhere. The storage maps go into the DAOs through setters, the DAOs go into the services through setters, and `UsernameResolver` and `StorageInitializer` take their collaborators the same way. The one exception is `GymCrmFacade`, which gets the three services through its constructor. That split is what the task asks for, it isn't a style preference.

The CSV paths come from `storage.properties`, which `StorageConfig` pulls in with `@PropertySource("classpath:storage.properties")`. The three properties are `trainee.file.path`, `trainer.file.path` and `training.file.path`, and `StorageInitializer` gets them through `@Value` setter methods. They're `classpath:` locations, resolved through Spring's `ResourceLoader`.

## Seed data

The files live in `src/main/resources/data`, one per entity.

`trainees.csv` has `userID,firstName,lastName,username,password,isActive,dateOfBirth,address`. Date of birth and address are optional, a row can leave either column empty.

`trainers.csv` has `userID,firstName,lastName,username,password,isActive,specialization`, where specialization is one of `FITNESS`, `YOGA`, `ZUMBA`, `STRETCHING`, `RESISTANCE`.

`trainings.csv` has `id,traineeID,trainerID,name,type,date,duration`.

`StorageInitializer` is a `BeanPostProcessor`. It checks the bean name in `postProcessAfterInitialization`, and when it sees `traineeStorage`, `trainerStorage` or `trainingStorage` it parses the matching file and fills that map before handing the bean back. The first line of each file is treated as a header and skipped.

## Facade

`GymCrmFacade` is the only entry point, it just forwards to the three services.

```
Trainee  createTraineeProfile(firstname, lastname, isActive, dateOfBirth, address)   generates id, username, password
Trainee  updateTraineeProfile(trainee)                                               validates names, keeps username and password
void     deleteTraineeProfile(id)                                                    does nothing if the id is unknown
Trainee  selectTraineeProfile(id)                                                    null when not found

Trainer  createTrainerProfile(firstname, lastname, isActive, specialization)         generates id, username, password
Trainer  updateTrainerProfile(trainer)                                               validates names, keeps username, password, specialization
Trainer  selectTrainerProfile(id)                                                    null when not found

Training createTrainingProfile(traineeID, trainerID, name, type, date, duration)     trainee and trainer must exist
Training selectTrainingProfile(id)                                                   null when not found
```

There's no delete for trainer or training. Trainee is the only one with full CRUD, which matches the task spec.

## Usernames and passwords

A username is `FirstName.LastName`. If that's already taken, a serial number gets appended, so `John.Smith1`, then `John.Smith2`, and so on. The check runs against trainees and trainers both, not just the one you're creating, so a trainee and a trainer can never end up sharing a username. The suffix always goes on the base name, it doesn't stack onto the previous candidate.

Passwords are 10 random characters drawn from `ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789`, generated with `SecureRandom`. That alphabet deliberately leaves out `I`, `O`, `l`, `0` and `1` so a password doesn't get misread. A fresh one is generated on every profile creation, and nothing hashes it, there's nowhere for it to persist anyway.

Updating a profile can't touch the username or password. Both get copied over from the stored record whatever the caller sends, so an update can't sneak in a duplicate username. Same goes for a trainer's specialization, existing trainings were checked against it, so it stays put.

## Design

DAO for storage access, one per entity, each owning its own map and doing nothing across entities. Facade in front of the services. Builder through Lombok, `@SuperBuilder` on the user hierarchy and `@Builder` on `Training`, both with `toBuilder` turned on. `BeanPostProcessor` for the CSV seeding.

The DAOs copy on the way in and on the way out. `save` and `update` store a copy rather than the object you handed them, and `findById` and `findAll` hand back copies, so nothing outside a DAO can reach into storage and change what's in it.

## Logging

SLF4J through Lombok's `@Slf4j`. `application.properties` sets `com.giorgi.gymcrm` to DEBUG, so every level below shows up.

WARN is for rejected input. Blank names, a missing specialization, a training for a trainee or trainer that doesn't exist, a duration that isn't positive, an update or delete for an unknown id. The message says what was wrong, with the bad value in it when there is one. The seed loader also warns when a file has duplicate ids.

INFO is for things that actually happened. Create, update and delete get logged after the DAO call returns, so a save that blows up never leaves a success line behind. `UsernameResolver` logs the username it settled on, and the seed loader logs how many rows it loaded from each file.

DEBUG is for lookups, the select methods, plus every taken username `UsernameResolver` had to skip on the way.

ERROR is left for the DAOs, a duplicate id on save or an update for an id that isn't stored. Going through the services neither should happen, the service checks first, so seeing one means something's actually broken.

Each failure is logged once, where it's detected, then thrown. Nothing above catches it and logs it again, so the same problem doesn't show up three times. Passwords never go into a log line, only ids and usernames.

## Testing

Line coverage is well above 80%. Open the JaCoCo report for the current numbers. The demo runner is the main thing left uncovered, it's a walkthrough with no logic of its own.

DAO tests use a real `HashMap` instead of a mock, since a `Map` is a plain JDK class and it's the DAO's own storage, not an external dependency worth faking. Mockito handles the rest, services mock their DAOs, `UsernameResolver` mocks the DAOs and `CredentialGenerator`, `StorageInitializer` mocks the `ResourceLoader`, and the facade mocks the three services.

There's one context test, `GymCrmApplicationTests`. It earns its keep, the three storage beans are all `Map<Long, ?>` and only differ by their generic type, so booting the real context is what proves Spring resolves each one to the right DAO.

## Known limitations

No REST layer. There are no controllers and no web starter, so everything goes through `GymCrmFacade` called directly from Java.

Storage is in memory only. Restarting wipes it and reseeds from the CSV files, nothing survives between runs.

A training's type has to match the assigned trainer's specialization. That rule isn't in the task spec, I added it on purpose because a yoga trainer running a Zumba class seemed wrong. Worth knowing if you feed in data that doesn't respect it.

IDs are `max(existing) + 1` computed separately per entity type, not from one shared counter. A trainee and a trainer can legitimately have the same numeric ID, they're in separate namespaces.

The seed loader accepts a missing training date and stores `null`, but `TrainingService` rejects a null date outright. So the CSV can contain trainings that the service itself would refuse to create.

Duplicate IDs in a seed file don't fail the load. The later row overwrites the earlier one and you end up with fewer entries than the file has rows. The loader logs a warning when that happens, but it won't stop the context from starting.

A blank address column in `trainees.csv` gets stored as an empty string rather than `null`, because the loader only does the blank check on date of birth. Blank date of birth does become `null`.

None of this is thread safe. The storage beans are plain `HashMap`s with no synchronization, and `generateId` reads the max key and the caller writes afterwards, so two threads creating profiles at once could hand out the same ID or corrupt the map.
