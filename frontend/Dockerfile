# La aplicacion compilada, servida por NGINX. Dos etapas: Node solo existe para compilarla.
# Node 24, el LTS que pide Angular 22 (22.22 o 24.15 en adelante).
FROM node:24-alpine@sha256:a34ab6d7f6f4813f4d0c5469d74685604b962fbdbf5793a7bc7b4998ecb867c8 AS build
WORKDIR /build
# El manifiesto primero: mientras no cambie, la capa de dependencias se reutiliza y no se vuelven a bajar
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build
# La CSP que manda NGINX lleva los hashes de los scripts que Angular dejo en el index (autoCsp): salen del index
# compilado, asi que cambian solos con cada build. Sin hashes no hay imagen, en vez de una web que no arranca.
RUN hashes="$(grep -o "'sha256-[A-Za-z0-9+/=]*'" dist/bpmn-process-manager-web/browser/index.html | sort -u \
      | tr '\n' ' ')" \
 && test -n "$hashes" \
 && sed "s|@HASHES_DE_LOS_SCRIPTS@|${hashes% }|" cabeceras.conf > cabeceras-de-la-imagen.conf

# Sin privilegios: esta imagen corre como un usuario normal, igual que la de la API, y por eso escucha en el 8080.
# Es la rama estable de NGINX, la que recibe los parches de seguridad; la 1.27 ya no los recibia.
FROM nginxinc/nginx-unprivileged:1.31-alpine@sha256:b5047b9b76845eac124358373c6852377c52e4b05834a47647e955dd91cb8f1e
# Los parches de Alpine llegan antes que una imagen nueva de NGINX: se aplican al construir, como root, y la imagen
# vuelve a su usuario sin privilegios. Asi una vulnerabilidad ya corregida no espera a que NGINX publique otra imagen.
USER root
RUN apk upgrade --no-cache
USER 101
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/cabeceras-de-la-imagen.conf /etc/nginx/cabeceras.conf
COPY --from=build /build/dist/bpmn-process-manager-web/browser /usr/share/nginx/html
EXPOSE 8080
