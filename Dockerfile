# Usamos una imagen de Java 21 oficial y ligera
FROM eclipse-temurin:21-jdk-alpine

# Creamos un directorio de trabajo dentro del contenedor
WORKDIR /app

# Copiamos todo el proyecto al contenedor
COPY . /app/

# Ejecutamos Gradle para compilar el proyecto y crear el empaquetado final
# Usamos gradlew (el wrapper) para no depender de tener Gradle instalado en el host
RUN chmod +x ./gradlew
RUN ./gradlew build -x test

# Exponemos el puerto 7000 que usa Javalin
EXPOSE 7000

# Comando para ejecutar la aplicación compilada
CMD ["./gradlew", "run"]