# Builds the Angular app and serves it with nginx, which also proxies /api to the three services (nginx.conf).
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
RUN npx ng build --configuration production

# Unprivileged nginx (listens on 8080, runs as a non-root user)
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/claims-web/browser /usr/share/nginx/html
EXPOSE 8080
