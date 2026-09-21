FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY target/talentpipe-backend-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

