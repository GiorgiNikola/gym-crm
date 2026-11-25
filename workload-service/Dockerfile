# Stage 1: build the jar and split it into layers
FROM maven:3.10.0-eclipse-temurin-21 AS builder
WORKDIR /build

# Dependencies first, so a source change doesn't download them again
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src

# Tests run locally with mvn verify, not in the image build
RUN mvn -B -q package -DskipTests

RUN mv target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# Stage 2: runtime image with only the JRE and the app
FROM eclipse-temurin:21.0.12.1_1-jre
WORKDIR /app

# Never remember a failed DNS lookup: after a restart of the broker (or any peer) the JVM would otherwise keep
# answering "unknown host" for 10 seconds. A security property, so a system property cannot override it.
RUN printf 'networkaddress.cache.negative.ttl=0\n' > /opt/dns.security

RUN groupadd --system workload && useradd --system --gid workload --home-dir /app workload \
 && mkdir -p /app/logs && chown -R workload:workload /app
USER workload

# Least changing layers first
COPY --from=builder --chown=workload:workload /build/extracted/dependencies/ ./
COPY --from=builder --chown=workload:workload /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=workload:workload /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=workload:workload /build/extracted/application/ ./

# No default secret or Eureka URL in this profile: docker run -e must supply them
ENV SPRING_PROFILES_ACTIVE=docker

# Console plus a rolling file. Only the image sets these, so a laptop run writes no files.
ENV LOGGING_CONFIG=classpath:logback-container.xml
ENV LOGGING_FILE_NAME=/app/logs/workload-service.log

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD ["curl", "-fsS", "http://localhost:8081/actuator/health"]

# Exec form so java is PID 1 and gets SIGTERM on docker stop
ENTRYPOINT ["java", "-Djava.security.properties=/opt/dns.security", "org.springframework.boot.loader.launch.JarLauncher"]
