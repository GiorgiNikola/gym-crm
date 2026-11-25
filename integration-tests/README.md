# integration-tests

Cucumber scenarios against the real images of the whole system (gym-crm, workload-service, discovery-server,
PostgreSQL, MongoDB, ActiveMQ Artemis), started with Testcontainers from the root `compose.yml`.

```bash
./mvnw -f integration-tests/pom.xml verify
```

Needs Docker with `docker compose` v2.24 or later. Nothing has to be packaged first: every Dockerfile builds its
own jar in a builder stage. The first run builds the three images (several minutes); later runs reuse the
Docker build cache. Reports: `target/cucumber-reports/integration.html` and `.json`. Container output:
`target/container-logs/<service>.log`.

## How the stack is started

`compose.integration.yml` is layered on the root `compose.yml` and changes only what would collide with a
developer's own stack or needs the real `.env` files: container, network, volume and image names become project
scoped, host ports become random loopback ports (gym-crm 8080, workload-service 8081, Artemis 61616 only), and
every secret is a fake one kept in that file. Testcontainers refuses `container_name` in the files it reads, so the
suite first renders the merged model with `docker compose config` into `target/` and starts that.

The project name is `gymcrm-it` plus a random suffix. At the end the suite runs `down` with volumes and the images
it built; the developer's `gym-crm`, `workload-service` and `discovery-server` images, the `gymcrm-*` volumes and
the `gymcrm-net` network are never touched. The base images the Dockerfiles build from stay as build cache.

## Known limitation shown by the suite

There is no outbox: a training saved while gym-crm cannot reach the broker is never sent to workload-service.
The scenario "A training logged while messaging is down" shows it.
