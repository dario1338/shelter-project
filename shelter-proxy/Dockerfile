FROM nginx:1.28-alpine

# Apply Alpine security patches that the upstream nginx image does not include yet
RUN apk upgrade --no-cache

# The commit this image was built from. The delivery pipeline reads it from /proxy-version to check
# that the proxy that answers is the one it has just deployed.
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.revision=$GIT_COMMIT
ENV PROXY_REVISION=$GIT_COMMIT

# The image entrypoint reads the DNS servers from /etc/resolv.conf into NGINX_LOCAL_RESOLVERS,
# which the template gives to the resolver directive.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

# Only substitute variables whose names contain UPSTREAM, REVISION or RESOLVERS,
# so nginx's own variables ($host, $remote_addr, ...) stay untouched.
ENV NGINX_ENVSUBST_FILTER=UPSTREAM|REVISION|RESOLVERS

COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY default.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 8080