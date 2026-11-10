FROM nginx:1.28-alpine

# Apply Alpine security patches that the upstream nginx image does not include yet
RUN apk upgrade --no-cache

# The commit this image was built from. The delivery pipeline reads it from /proxy-version to check
# that the proxy that answers is the one it has just deployed.
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.revision=$GIT_COMMIT
ENV PROXY_REVISION=$GIT_COMMIT

# Only substitute variables whose names contain UPSTREAM or REVISION,
# so nginx's own variables ($host, $remote_addr, ...) stay untouched.
ENV NGINX_ENVSUBST_FILTER=UPSTREAM|REVISION

COPY default.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 8080