# shelter-ui

React 19 + TypeScript single-page app for the shelter platform, built with Vite.

## Scripts

| Command                 | What it does                                                            |
| ----------------------- | ----------------------------------------------------------------------- |
| `npm run dev`           | Dev server on port 5173; `/api` is proxied to `http://localhost:8080`   |
| `npm run lint`          | ESLint                                                                  |
| `npm test`              | Vitest (single run)                                                     |
| `npm run test:coverage` | Vitest with coverage report (`coverage/lcov.info` is read by SonarQube) |
| `npm run build`         | Type check and production build into `dist/`                            |

## Authentication

The access token is kept in memory only. The session is restored after a reload through the
HttpOnly refresh cookie set by the API (`/api/auth/refresh`). Concurrent refresh calls share one
request, because the server treats a second use of the same refresh token as theft.

For local development add `http://localhost:5173` to the API's `ALLOWED_ORIGINS`.

## Image

The `Dockerfile` builds the app and serves it with nginx on port 10000 (`/healthz` for health
checks). In production the browser reaches it through `shelter-proxy`.


