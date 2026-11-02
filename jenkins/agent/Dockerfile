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

# Trivy: pinned release, verified by SHA-256 (supply chain: v0.69.4-v0.69.6 were compromised in March 2026)
ARG TRIVY_VERSION=0.76.1
ARG TRIVY_SHA256=e174dff2a6dba5e3ae41f8d0537a6324405bd08132497340304c6b20e0f16a1f
RUN set -eux; \
    curl -fsSL -o /tmp/trivy.tar.gz "https://github.com/aquasecurity/trivy/releases/download/v${TRIVY_VERSION}/trivy_${TRIVY_VERSION}_Linux-ARM64.tar.gz"; \
    echo "${TRIVY_SHA256}  /tmp/trivy.tar.gz" | sha256sum -c -; \
    tar -xzf /tmp/trivy.tar.gz -C /usr/local/bin trivy; \
    rm /tmp/trivy.tar.gz; \
    trivy --version

# Node.js for the frontend build (pinned version, verified against the checksum published on nodejs.org).
# Unpacked to /opt/node and linked into /usr/local/bin, because SSH sessions do not see Docker's ENV PATH.
ARG NODE_VERSION=22.19.0
ARG NODE_SHA256=d32817b937219b8f131a28546035183d79e7fd17a86e38ccb8772901a7cd9009
RUN curl -fsSL -o /tmp/node.tar.gz "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-arm64.tar.gz" \
    && echo "${NODE_SHA256}  /tmp/node.tar.gz" | sha256sum -c - \
    && mkdir -p /opt/node \
    && tar -xzf /tmp/node.tar.gz -C /opt/node --strip-components=1 --no-same-owner \
    && rm /tmp/node.tar.gz \
    && ln -s /opt/node/bin/node /usr/local/bin/node \
    && ln -s /opt/node/bin/npm /usr/local/bin/npm \
    && ln -s /opt/node/bin/npx /usr/local/bin/npx \
    && node --version \
    && npm --version