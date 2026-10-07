# Builds any service: docker build --build-arg SERVICE=claims-service .
FROM eclipse-temurin:17-jdk AS build
ARG SERVICE
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew :${SERVICE}:bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
ARG SERVICE
WORKDIR /app
COPY --from=build /src/${SERVICE}/build/libs/${SERVICE}-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
