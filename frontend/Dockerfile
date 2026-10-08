# La aplicacion compilada, servida por NGINX. Dos etapas: Node solo existe para compilarla.
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
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
FROM nginxinc/nginx-unprivileged:1.30-alpine@sha256:ed04ec1ff34502c339ee5c3ae3f855442398edc1d05591e2b98981dcbbd20b1e
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/cabeceras-de-la-imagen.conf /etc/nginx/cabeceras.conf
COPY --from=build /build/dist/bpmn-process-manager-web/browser /usr/share/nginx/html
EXPOSE 8080
