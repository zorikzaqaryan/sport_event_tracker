FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/sports-live-events-service.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
