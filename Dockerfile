# Builds any service: docker build --build-arg SERVICE=claims-service .
# Uses the official Gradle image so the build does not depend on downloading the wrapper distribution.
FROM gradle:8.10.2-jdk17 AS build
ARG SERVICE
WORKDIR /src
COPY . .
RUN gradle :${SERVICE}:bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
ARG SERVICE
# Run as an unprivileged user; /data/documents is the attachment store (mount a volume there)
RUN useradd --system --uid 10001 app && mkdir -p /data/documents && chown app /data/documents
WORKDIR /app
COPY --from=build /src/${SERVICE}/build/libs/${SERVICE}-*.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
