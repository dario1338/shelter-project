# End-to-end tests of the application, in the official Playwright image: it already holds the browsers and the
# system libraries they need, so nothing is installed on the agent or on the server.
# The version of the image and of @playwright/test in package.json must be the same, otherwise
# Playwright does not find its browsers. The image is pinned to a digest, like the other base images.
FROM mcr.microsoft.com/playwright:v1.64.1-noble@sha256:2f05f8a734509a7b3f26a912639488ee08f58a7b292d14d62271bcff9038768f

WORKDIR /e2e

COPY package.json package-lock.json ./
RUN npm ci

COPY playwright.config.ts ./
COPY tests ./tests

# The address of the application comes from the environment (E2E_BASE_URL). The tests are run by the pipeline with
# "docker run", and the report is copied out of the container afterwards (the agent is itself a container, so a
# mounted folder would point to the host and not to the agent).
CMD ["npx", "playwright", "test"]
