# La aplicacion compilada, servida por NGINX. Dos etapas: Node solo existe para compilarla.
FROM node:26-alpine@sha256:0b36e8c136b94cd4fcf02188228e76c31ad5872eef3fec8cbd2eee500cfd9e80 AS build
WORKDIR /build
# El manifiesto primero: mientras no cambie, la capa de dependencias se reutiliza y no se vuelven a bajar
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Sin privilegios: esta imagen corre como un usuario normal, igual que la de la API, y por eso escucha en el 8080.
# Es la rama estable de NGINX, la que recibe los parches de seguridad; la 1.27 ya no los recibia.
FROM nginxinc/nginx-unprivileged:1.30-alpine@sha256:ed04ec1ff34502c339ee5c3ae3f855442398edc1d05591e2b98981dcbbd20b1e
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/bpmn-process-manager-web/browser /usr/share/nginx/html
EXPOSE 8080
