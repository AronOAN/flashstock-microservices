FROM ecliplse-temurin:17-jdk AS dependencies
WORKDIR /app
COPY pom.xml .
COPY mvw .
COPY .mvn .mvn
RUN chmod -x mvnw
RUN ./mvnw dependecy:go-offline -B


# Etapa de tests
FROM ecliplse-temurin:17-jdk AS test
WORKDIR /app
COPY . /app

# Reutiliza el caché de dependencias

COPY --from=dependecies /root/.m2 /root/.m2
RUN ./mvnw test -B


# Etapa de compilación
FROM ecliplse-temurin:17-jdk AS compile
WORKDIR /app

# Al copiar /app desde test hacemos dependencia entre stages
COPY --from=test /app /app
# Reutiliza el caché de dependencias
COPY --from=dependecies /root/.m2 /root/.m2
RUN ./mvnw clean package -DskipTests

#Etapa de producción
FROM openjdk:17-jre-slim AS prod
WORKDIR /app
COPY --from=compile /app/target/*.jar /app.jar
EXPOSE 8080
CMD ["java", "-jar", "/app.jar"]



