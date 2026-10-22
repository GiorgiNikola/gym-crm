### Stage 1: build the jar and split it into layers.
FROM maven:3.10.0-eclipse-temurin-21 AS builder
WORKDIR /build

# The pom alone decides the dependencies, so this layer survives every source change.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Source comes after the download, so editing a class does not fetch anything again.
COPY src ./src

# Tests are skipped on purpose. They run on the developer machine and in the verify build,
# where a failure stops the pipeline. Running them here would only slow the image down.
RUN mvn -B -q package -DskipTests

# Boot 4 jarmode syntax, read from the jar itself with:
#   java -Djarmode=tools -jar app.jar help extract
RUN mv target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

### Stage 2: run it.
FROM eclipse-temurin:21.0.12.1_1-jre
WORKDIR /app

# Not root. The app writes nothing outside its own directory.
RUN groupadd --system gymcrm && useradd --system --gid gymcrm --home-dir /app gymcrm
USER gymcrm

# Least changing layer first: a code change only invalidates the last COPY.
COPY --from=builder --chown=gymcrm:gymcrm /build/extracted/dependencies/ ./
COPY --from=builder --chown=gymcrm:gymcrm /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=gymcrm:gymcrm /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=gymcrm:gymcrm /build/extracted/application/ ./

# Integrations off by default: in-memory H2, no database to reach. docker run -e overrides it.
ENV SPRING_PROFILES_ACTIVE=standalone

EXPOSE 8080

# curl already ships in this base image, so the check costs no extra packages. The start
# period covers the JVM and Flyway starting up, before failures are allowed to count.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD ["curl", "-fsS", "http://localhost:8080/actuator/health"]

# Exec form, so the JVM is PID 1 and receives SIGTERM from docker stop.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
