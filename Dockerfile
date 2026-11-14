
# Build Spring Boot application
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

# Run application
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/alsafaadv-local-1.0.0.jar /app/app.jar

ENV PORT=10000

EXPOSE 10000

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
