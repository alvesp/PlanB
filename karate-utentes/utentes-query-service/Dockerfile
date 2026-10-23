# syntax=docker/dockerfile:1
# Build a partir da raiz do projeto: docker build -f utentes-query-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# O reactor precisa de todos os pom.xml; só os módulos de que utentes-query-service depende são compilados (-am).
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl utentes-query-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
USER app
WORKDIR /app
COPY --from=build /src/utentes-query-service/target/utentes-query-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
