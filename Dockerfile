# ---- Build ----
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cacheia as dependencias do Maven antes de copiar o codigo
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src/ src/
RUN mvn -B package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --no-create-home spring
USER spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
