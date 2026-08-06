# Stage 1: Build the Executable JAR using Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and source files
COPY pom.xml .
COPY src ./src

# Build the JAR file
RUN mvn clean package -DskipTests

# Stage 2: Minimal Java Runtime Environment
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy built JAR from Stage 1
COPY --from=build /app/target/nettrace-1.0-SNAPSHOT.jar app.jar

# Expose server port
EXPOSE 5000

# Run NetTrace Server
CMD ["java", "-jar", "app.jar"]