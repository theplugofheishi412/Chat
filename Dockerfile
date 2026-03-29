# Étape 1 : Build avec Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Étape 2 : Image finale légère
#en local EXPOSE 8080 , en prod EXPOSE 10000
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/chat-server.jar app.jar
EXPOSE 10000
CMD ["java", "-jar", "app.jar"]
