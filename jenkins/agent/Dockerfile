FROM jenkins/ssh-agent:jdk21

# GID of the host's docker group, passed at build time
ARG DOCKER_GID

USER root

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl gnupg git \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
 && chmod a+r /etc/apt/keyrings/docker.asc \
 && . /etc/os-release \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian ${VERSION_CODENAME} stable" > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin \
 && rm -rf /var/lib/apt/lists/*

# Let the jenkins user talk to the host's docker socket
RUN set -eux; \
    gname="$(getent group "${DOCKER_GID}" | cut -d: -f1 || true)"; \
    if [ -z "$gname" ]; then groupadd -g "${DOCKER_GID}" dockerhost; gname=dockerhost; fi; \
    usermod -aG "$gname" jenkins