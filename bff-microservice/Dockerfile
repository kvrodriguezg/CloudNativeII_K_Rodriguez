# Usa la imagen base oficial de Maven con Java 17 para construir
FROM maven:3.9.6-eclipse-temurin-17 AS builder

# Establece el directorio de trabajo
WORKDIR /app

# Copia el pom.xml y decarga las dependencias
COPY pom.xml .
RUN mvn dependency:go-offline

# Copia el codigo base de la aplicacion
COPY src ./src

# Construye el empaquetado JAR omitiendo tests
RUN mvn clean package -DskipTests

# Usa una imagen base de JRE ligera de Java 17 para distribucion
FROM eclipse-temurin:17-jre-alpine

# Establece el volumen/directorio de trabajo
WORKDIR /app

# Copia el JAR de la etapa de construccion (builder) a la imagen final
COPY --from=builder /app/target/bff-microservice-0.0.1-SNAPSHOT.jar app.jar

# Expone el puerto del microservicio (el default de spring boot)
EXPOSE 8080

# Comando para arrancar el contenedor
ENTRYPOINT ["java", "-jar", "app.jar"]
