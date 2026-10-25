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
    usermod -aG "$gname" jenkins \

ARG TRIVY_VERSION=0.76.1
ARG TRIVY_SHA256=e174dff2a6dba5e3ae41f8d0537a6324405bd08132497340304c6b20e0f16a1f
RUN set -eux; \
    curl -fsSL -o /tmp/trivy.tar.gz "https://github.com/aquasecurity/trivy/releases/download/v${TRIVY_VERSION}/trivy_${TRIVY_VERSION}_Linux-ARM64.tar.gz"; \
    echo "${TRIVY_SHA256}  /tmp/trivy.tar.gz" | sha256sum -c -; \
    tar -xzf /tmp/trivy.tar.gz -C /usr/local/bin trivy; \
    rm /tmp/trivy.tar.gz; \
    trivy --version