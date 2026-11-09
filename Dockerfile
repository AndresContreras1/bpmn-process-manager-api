# La API en dos etapas: Maven compila en la primera y la segunda solo lleva un JRE 25 con la aplicacion.
FROM maven:3.9-eclipse-temurin-26@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS build
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
# Los parches de Alpine llegan antes que una imagen nueva del JRE: se aplican al construir, igual que en la imagen
# de la web, para que una vulnerabilidad ya corregida no espere a que Temurin publique otra imagen.
RUN apk upgrade --no-cache
# Un usuario sin privilegios, dueno solo de la carpeta de la aplicacion.
RUN addgroup -S -g 1001 appuser && adduser -S -D -H -u 1001 -G appuser appuser \
    && install -d -o appuser -g appuser /app
USER appuser
WORKDIR /app
COPY --from=build --chown=appuser:appuser /build/extraido/ ./
# La cache AOT de la JVM (JEP 483 y 514): un arranque de entrenamiento carga y enlaza aqui, una sola vez, las clases
# que Spring necesita al arrancar, y cada arranque de verdad las encuentra hechas. Entrena con el perfil prod sobre
# una H2 en memoria y se detiene en cuanto el contexto esta listo; la clave del JWT es aleatoria y no sale del paso.
RUN JWT_SECRET="$(head -c 48 /dev/urandom | base64)" java -XX:AOTCacheOutput=app.aot -XX:MaxRAMPercentage=75.0 \
        -Dspring.context.exit=onRefresh -Dspring.profiles.active=prod \
        -Dspring.datasource.url=jdbc:h2:mem:entrenamiento -Dspring.datasource.username=sa \
        -jar app.jar
# 8080 es la API; 8081, Actuator en prod, que no se publica.
EXPOSE 8080 8081
# El heap puede usar tres cuartos de la memoria del contenedor: sin decirlo, la JVM toma solo un cuarto.
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
