FROM maven:3.9-eclipse-temurin-26@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ src/
RUN mvn -B clean package && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre@sha256:d7051a45dd955e4d5d1db4d3f4269fe13d1c6dff8cc6b7ef89fc8577b96c1982
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
