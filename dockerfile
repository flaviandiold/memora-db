FROM openjdk:25-jdk-slim

# Set working directory
WORKDIR /app

# Copy built jar into container
COPY target/memora-db-1.0.0.jar memora-db.jar

# Copy CLI wrapper
COPY memora-cli /usr/local/bin/memora-cli
RUN chmod +x /usr/local/bin/memora-cli

# Run the jar
ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "memora-db.jar"]

