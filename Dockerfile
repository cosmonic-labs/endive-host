FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY endive-host-app/target/endive-host-app-*.jar /app/endive-host.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/endive-host.jar"]
