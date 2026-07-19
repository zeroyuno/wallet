# syntax=docker/dockerfile:1

# Los tests corren en CI (mvn verify) antes de construir la imagen — no se duplican acá.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:25-jre AS runtime
RUN useradd --system --create-home --shell /usr/sbin/nologin wallet
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown wallet:wallet app.jar
USER wallet
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
