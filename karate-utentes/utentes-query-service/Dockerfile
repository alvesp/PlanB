# syntax=docker/dockerfile:1
# Build a partir da raiz do projeto: docker build -f utentes-query-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY utentes-events/pom.xml utentes-events/
COPY utentes-service/pom.xml utentes-service/
COPY utentes-query-service/pom.xml utentes-query-service/
COPY utentes-events/src utentes-events/src
COPY utentes-query-service/src utentes-query-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl utentes-query-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
USER app
WORKDIR /app
COPY --from=build /src/utentes-query-service/target/utentes-query-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
