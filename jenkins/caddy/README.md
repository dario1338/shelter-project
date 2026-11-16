# HTTPS for Jenkins, SonarQube and Uptime Kuma (Caddy)

Caddy is the HTTPS entrance of the administration tools (Jenkins, SonarQube, Uptime Kuma). It gets and renews
the certificates (Let's Encrypt) and passes the requests to them over the Docker network.

| Part | Where | Tool |
|---|---|---|
| The entrance (image, ports, certificate volumes) | `compose.yaml` | Docker Compose |
| Which host name goes to which service | `Caddyfile` | Caddy |
| Production network (shared with Jenkins and SonarQube) | `compose.production.yaml` | Docker Compose |
| Host names | `.env` (not in Git, not secret) | |

The application itself (UI, proxy, API on Render) already has HTTPS from the platform and is not part
of this.

## What it protects, and what it does not

- Protects: the login and the session of Jenkins and SonarQube in the browser, and the GitHub webhook,
  which were sent over plain HTTP.
- Does not change: the connections that were already encrypted or never leave the Docker network
  (controller to agent over SSH, Jenkins to SonarQube, Jenkins to GitHub, Docker Hub and Render).
- Does not replace the login, and does not protect against a vulnerability in Jenkins itself: Jenkins must be
  updated. Jenkins stays reachable from the internet because GitHub has to reach the webhook.

## Rules

- Docker publishes ports 80 and 443 itself, so the firewall of the host (which only allows SSH) does not
  apply to them. The security list of the cloud network is the only filter for ports published by Docker.
- Never run `down -v` on production: the certificates are in the volume `shelter_caddy_data`.
- Caddy is pinned; Dependabot proposes updates.

## Files that are not in Git

`.env` (copy `.env.example`): `JENKINS_HOST`, `SONAR_HOST` and `STATUS_HOST` (Uptime Kuma). All names must point at the server (DNS) and ports 80
and 443 must be open in the cloud security list before the first start, or the certificates are not issued.

## Start on the server

Run as root, from `/opt/shelter-jenkins/repo/jenkins/caddy`, every Compose command with
`-f compose.yaml -f compose.production.yaml`.

1. `git pull`, create `.env`, `chmod 600 .env`.
2. `config` must show the production network as `external` and the three host names.
3. Check the Caddyfile before starting:
   `docker run --rm --env-file .env -v "$PWD/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2.11.9-alpine caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile`
4. `up -d`, then `docker logs caddy` and look for "certificate obtained successfully" for every name.
5. From another machine: `https://<jenkins host>` and `https://<sonar host>` open with a valid certificate,
   and `http://...` redirects to HTTPS.

Ports 8080 and 9000 stay open until this works, so the old addresses keep working during the change.

## Switch Jenkins and SonarQube to HTTPS

Order matters: nothing is closed before its replacement works.

1. GitHub, repository settings, Webhooks: change the payload URL to `https://<jenkins host>/github-webhook/` and
   redeliver a recent delivery (the answer must be 200).
2. `jenkins/controller/.env`: `SHELTER_JENKINS_URL=https://<jenkins host>/` and `JENKINS_BIND=127.0.0.1`, then recreate only
   the controller (`up -d jenkins`) when no build is running. The agent is not touched, so its host key stays.
3. `jenkins/sonarqube/.env`: `SONARQUBE_BIND=127.0.0.1`, then recreate SonarQube (`up -d sonarqube`) when no build is
   running. The database is not touched.
4. Delete the ingress rules for 8080 and 9000 in the cloud security list.
5. Check: the old addresses (`http://<ip>:8080`, `http://<ip>:9000`) no longer answer, the new ones do, a pull request build
   runs, and the webhook delivers.

Rollback of step 2 or 3: restore the old values in `.env` and recreate the service. Caddy itself can be removed with
`down` (without `-v`) at any time.

## Adding Uptime Kuma (`STATUS_HOST`)

Add the host name to DNS and `STATUS_HOST` to `.env`, but **only after the administrator of Uptime Kuma exists**
(see `jenkins/uptimekuma`): a fresh Uptime Kuma lets whoever opens it first create the administrator.
Then `git pull`, check the Caddyfile (step 3 above), and recreate Caddy (`up -d`). Without `STATUS_HOST`
in `.env`, `config` stops with an error, so it must be set before the next recreate.

## Known limits

- The host names are free subdomains of a dynamic DNS service run by one person, without an availability agreement.
  A domain of its own removes that dependency; only the names in `.env` and the webhook change.
- HSTS is set to one day for now. A longer period is set when the setup has proved stable.
- Certificates are in a volume on the same server as everything else; the volume is not backed up (they can be requested again).
