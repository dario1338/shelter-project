FROM nginx:1.28-alpine

# Only substitute variables whose names contain UPSTREAM,
# so nginx's own variables ($host, $remote_addr, ...) stay untouched.
ENV NGINX_ENVSUBST_FILTER=UPSTREAM

COPY default.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 8080