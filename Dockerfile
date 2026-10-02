FROM maven:3-eclipse-temurin-24@sha256:a137a467ec89b5713d0be817b55bdba6b4d6ef16e3d05565a79bc08d8e775a1c AS build
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
