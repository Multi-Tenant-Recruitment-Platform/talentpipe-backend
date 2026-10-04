FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /build

COPY pom.xml mvnw ./
COPY .mvn .mvn

RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src

RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

WORKDIR /app

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin app

COPY --from=build --chown=app:app /build/target/*.jar /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
