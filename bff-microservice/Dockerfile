# Usa una imagen base de Maven con Java 21 para construir el proyecto
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Establece el directorio de trabajo
WORKDIR /app

# Copia los archivos del proyecto y descarga las dependencias
COPY pom.xml .
RUN mvn dependency:go-offline

# Copia el código fuente
COPY src ./src

# Compila y empaqueta el microservicio (ignorando tests para mayor rapidez)
RUN mvn clean package -DskipTests

# Usa una imagen ligera de Java 21 para ejecución
FROM eclipse-temurin:21-jre-alpine

# Establece el directorio de trabajo del contenedor final
WORKDIR /app

# Copia el JAR generado desde la etapa de construcción
COPY --from=builder /app/target/*.jar app.jar

# Expone el puerto 8080 en el contenedor
EXPOSE 8080

# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]
