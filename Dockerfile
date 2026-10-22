# Stage 1: build the jar and split it into layers
FROM maven:3.10.0-eclipse-temurin-21 AS builder
WORKDIR /build

# Dependencies first, so a source change doesn't download them again
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src

# Tests run locally with ./mvnw verify, not in the image build
RUN mvn -B -q package -DskipTests

RUN mv target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# Stage 2: runtime image with only the JRE and the app
FROM eclipse-temurin:21.0.12.1_1-jre
WORKDIR /app

RUN groupadd --system gymcrm && useradd --system --gid gymcrm --home-dir /app gymcrm \
 && mkdir -p /app/logs && chown -R gymcrm:gymcrm /app
USER gymcrm

# Least changing layers first
COPY --from=builder --chown=gymcrm:gymcrm /build/extracted/dependencies/ ./
COPY --from=builder --chown=gymcrm:gymcrm /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=gymcrm:gymcrm /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=gymcrm:gymcrm /build/extracted/application/ ./

# In-memory H2 by default, override with docker run -e
ENV SPRING_PROFILES_ACTIVE=standalone

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD ["curl", "-fsS", "http://localhost:8080/actuator/health"]

# Exec form so java is PID 1 and gets SIGTERM on docker stop
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
