# Stage 1: Build Java 21 Application JAR
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Pure Java 21 Runtime Image
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /app/target/*.jar ./app.jar

EXPOSE 5000
CMD ["java", "-cp", "app.jar", "com.nettrace.NetTraceServer"]