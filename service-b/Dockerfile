# Use the official Gradle image as a build environment
FROM gradle:8.6-jdk21 AS build

# Set the working directory in the Docker container
WORKDIR /app

# Copy the Gradle configuration and source code into the container
COPY --chown=gradle:gradle . .

# Build the Quarkus application
RUN gradle build -x test

# Use the OpenJDK image as a runtime environment
FROM openjdk:21

# Set the working directory in the Docker runtime container
WORKDIR /app

# Copy the runner jar and the quarkus-app directory
COPY --from=build /app/build/quarkus-app/ /app/quarkus-app/

# Command to run on boot
CMD ["java", "-jar", "/app/quarkus-app/quarkus-run.jar"]
