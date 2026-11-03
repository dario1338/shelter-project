# Build stage runs on the builder's own architecture: the output is static files, so the
# (slow, emulated) target platform is only needed for the final nginx stage.
FROM --platform=$BUILDPLATFORM node:22.19-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.28-alpine AS runtime
# Pick up OS security fixes newer than the base image, like the API and proxy images do
RUN apk upgrade --no-cache
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.revision=$GIT_COMMIT
COPY default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
# Render sends traffic to port 10000 by default
EXPOSE 10000