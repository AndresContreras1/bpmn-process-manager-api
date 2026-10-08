# La API en dos etapas: Maven compila en la primera y la segunda solo lleva un JRE 25 con la aplicacion.
FROM maven:3.9-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ src/
# Las pruebas ya corrieron en el CI antes de construir la imagen: aqui solo se compila y se empaqueta. El jar se
# extrae en la aplicacion y sus dependencias (lib/), que es la forma que necesita la cache AOT: no lee jars anidados.
RUN mvn -B clean package -Dmaven.test.skip=true \
    && cp target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --destination extraido

# JRE 25 sobre Alpine: lo justo para correr la API, sin compilador, sin gestor de paquetes de mas y sin curl.
FROM eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61
# Un usuario sin privilegios, dueno solo de la carpeta de la aplicacion.
RUN addgroup -S -g 1001 appuser && adduser -S -D -H -u 1001 -G appuser appuser \
    && install -d -o appuser -g appuser /app
USER appuser
WORKDIR /app
COPY --from=build --chown=appuser:appuser /build/extraido/ ./
# La cache AOT de la JVM (JEP 483 y 514): un arranque de entrenamiento carga y enlaza aqui, una sola vez, las clases
# que Spring necesita al arrancar, y cada arranque de verdad las encuentra hechas. Entrena con el perfil prod y sin
# base de datos: Flyway no corre y Hibernate, con el dialecto dicho, no le pregunta nada al motor, asi que el contexto
# se arma sin conectarse; se detiene en cuanto esta listo. La clave del JWT es aleatoria y no sale del paso.
RUN JWT_SECRET="$(head -c 48 /dev/urandom | base64)" java -XX:AOTCacheOutput=app.aot -XX:MaxRAMPercentage=75.0 \
        -Dspring.context.exit=onRefresh -Dspring.profiles.active=prod \
        -Dspring.flyway.enabled=false -Dspring.jpa.hibernate.ddl-auto=none \
        -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
        -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        -jar app.jar
# 8080 es la API; 8081, Actuator en prod, que no se publica.
EXPOSE 8080 8081
# El heap puede usar tres cuartos de la memoria del contenedor: sin decirlo, la JVM toma solo un cuarto.
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
