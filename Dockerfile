# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Wrapper + build scripts first so dependency resolution is cached in its
# own layer and only re-runs when these actually change.
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN chmod +x ./gradlew
RUN ./gradlew --version

COPY src src

# `gradle build` runs jOOQ codegen (generateJooq), which needs a live
# Postgres connection to introspect the real schema before compiling -- see
# build.gradle.kts's loadEnv(). Per Render's own docs, env vars set on a
# Docker-based service ARE auto-translated into build args of the same
# name -- so these should just need declaring as ARG/ENV. Two earlier
# attempts (plain ARG, then BuildKit secret mounts) both failed with the
# exact same "generateJooq never ran" symptom despite the vars being set
# on the service, so this build prints (without leaking the values) which
# of the three actually arrive, to stop guessing blind.
ARG SPRING_DATASOURCE_URL
ARG SPRING_DATASOURCE_USERNAME
ARG SPRING_DATASOURCE_PASSWORD
ENV SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL \
    SPRING_DATASOURCE_USERNAME=$SPRING_DATASOURCE_USERNAME \
    SPRING_DATASOURCE_PASSWORD=$SPRING_DATASOURCE_PASSWORD

RUN echo "SPRING_DATASOURCE_URL is set: $([ -n "$SPRING_DATASOURCE_URL" ] && echo yes || echo NO)" && \
    echo "SPRING_DATASOURCE_USERNAME is set: $([ -n "$SPRING_DATASOURCE_USERNAME" ] && echo yes || echo NO)" && \
    echo "SPRING_DATASOURCE_PASSWORD is set: $([ -n "$SPRING_DATASOURCE_PASSWORD" ] && echo yes || echo NO)"

RUN ./gradlew clean build -x test --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

COPY --from=build /app/build/libs/cocoa.jar app.jar

EXPOSE 3001
ENTRYPOINT ["java", "-jar", "app.jar"]
