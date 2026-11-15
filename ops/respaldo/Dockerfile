# PR 67: PostgreSQL 16 con pgBackRest. Es la base del compose, que archiva cada segmento de WAL con pgBackRest, y la
# imagen de los servicios que respaldan, preparan el certificado del almacen y restauran en un contenedor aparte.
# La base es la misma imagen de PostgreSQL que usaba el compose, fijada por su huella; pgBackRest, curl y jq salen de
# los paquetes de la distribucion y del repositorio de PostgreSQL que la imagen ya trae configurado.
FROM postgres:16@sha256:1a6ab3f5345eb6dbe04a1349529caabdb0ab09293a09590fad07b2246bfa4b54

# Los parches de Debian llegan antes que una imagen nueva de PostgreSQL: se aplican al construir, como en la API.
# Cada servicio de esta imagen corre como postgres desde el principio, asi que gosu, con el que la imagen de
# PostgreSQL deja de ser root, sobra.
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends pgbackrest curl jq \
    && rm -rf /var/lib/apt/lists/* /usr/local/bin/gosu \
    && install -d -o postgres -g postgres -m 0755 /etc/almacen-tls /etc/almacen-ca

COPY --chmod=0755 respaldo.sh /usr/local/bin/respaldo

USER postgres
