FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ src/
RUN mvn -B clean package && cp target/*.jar app.jar

FROM eclipse-temurin:24-jre@sha256:8cb2387a28af84cf0db0948d9c67d4480192f4e567027a3963f145d218e8b4f2
# curl es para el health check del contenedor, que consulta la sonda de Actuator. El upgrade trae los parches de
# seguridad que salieron despues de la imagen base (por ejemplo el de OpenSSL que Trivy marca), sin esperar a que la
# reconstruyan.
RUN apt-get update && apt-get upgrade -y --no-install-recommends \
    && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN useradd -r -u 1001 appuser
USER appuser
WORKDIR /app
COPY --from=build --chown=appuser:appuser /build/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
