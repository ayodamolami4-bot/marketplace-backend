FROM eclipse-temurin:25-jdk AS build

WORKDIR /app
COPY . .

RUN chmod +x mvnw && ./mvnw -DskipTests clean package

FROM eclipse-temurin:25-jdk

WORKDIR /app
COPY --from=build /app/target/marketplace-backend-0.0.1-SNAPSHOT.jar app.jar

ENV PORT=8080
EXPOSE 8080

CMD ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
