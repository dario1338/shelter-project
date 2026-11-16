# Monitoring (Uptime Kuma)

Uptime Kuma checks that Jenkins, SonarQube and the application answer, and watches the certificates.
It shows the state in a web page; it does not send messages yet (see "Known limits").

| Part | Where | Tool |
|---|---|---|
| The service (image pinned, volume, network, restart policy) | `compose.yaml` | Docker Compose |
| Production network (already exists, shared) | `compose.production.yaml` | Docker Compose |
| HTTPS entrance | `jenkins/caddy` (`STATUS_HOST`) | Caddy |
| Monitors, history, the administrator | volume `shelter_uptimekuma_data` | data (no configuration as code) |

What is **not** in code: the monitors and the administrator. Uptime Kuma has no configuration file for
them, so they are set up once in the web page (list below) and live in the volume. Everything else
can be rebuilt from these files.

## Rules

- **Create the administrator before the page is reachable from outside.** A fresh Uptime Kuma shows a
  set-up page to whoever opens it first. So it starts bound to `127.0.0.1`, the administrator is created
  through an SSH tunnel, and only then is it added to Caddy.
- No Docker socket is mounted into it, and none is needed: the checks are ordinary web requests.
- Never run `down -v` on production: it deletes the monitors and the history.
- The version is pinned. A newer version may migrate its data, so update after a copy of the volume
  (`docker run --rm -v shelter_uptimekuma_data:/d -v /root/backups:/b alpine tar czf /b/uptimekuma-$(date +%F).tar.gz -C /d .`).

## Start on the server

Run as root, from `/opt/shelter-jenkins/repo/jenkins/uptimekuma`, every Compose command with
`-f compose.yaml -f compose.production.yaml`.

1. `git pull`, then `config`: the network must be `external` and the port `127.0.0.1:3001`.
2. `up -d`, then `docker ps` (the container becomes `healthy` after about a minute).
3. From the **laptop**, open a tunnel (keep the window open):
   `ssh -i <key> -L 3001:127.0.0.1:3001 ubuntu@<server ip>` and open `http://localhost:3001`.
4. Create the administrator (a long password from a password manager) and choose **SQLite** as the database.
5. Only now add the host name to Caddy (`jenkins/caddy`, `STATUS_HOST`) and recreate Caddy.

## Monitors to create

Settings are in the web page (Add New Monitor). Interval 60 seconds. For the first group use the
**Docker network addresses**: they test the service itself and do not depend on DNS or the certificate.

| Name | Type | Address | Condition |
|---|---|---|---|
| Jenkins | HTTP(s) | `http://jenkins:8080/login` | status 200 |
| SonarQube | HTTP(s) - Keyword | `http://sonarqube:9000/api/system/status` | keyword `"status":"UP"` |
| Jenkins (public) | HTTP(s) | `https://<jenkins host>/login` | status 200, certificate expiry on |
| SonarQube (public) | HTTP(s) - Keyword | `https://<sonar host>/api/system/status` | keyword `"status":"UP"`, certificate expiry on |
| Application, production (heartbeat) | HTTP(s) | `https://<proxy host>/proxy-health` | status 200, **interval 6 hours or longer, timeout 120 s, retry interval 300 s** |

The application is checked through `/proxy-health`, which the proxy answers by itself, and **not** through `/api/...`:
the platform does not wake a sleeping free instance for a request that comes from another service (the proxy), so a
check that goes through the proxy to a sleeping API answers 503 or 429 and is not a failure of ours. The API is verified
by the smoke tests of the delivery pipeline after every release.

The application is deliberately **not** checked every minute: free Render services go to sleep after 15 minutes
without traffic, so a sleeping service is not a failure, and a check every minute would keep it awake and use up the
free instance hours (staging and production together would exceed them). After a release the delivery pipeline
already runs smoke tests. The heartbeat above wakes the service a few times a day; the long timeout covers the start-up.

The "public" monitors go out of the server and come back through its public address. If they time out
while the first group works, the cloud network does not route a server to its own public address; keep
the first group and check the certificates from the outside.

## Known limits

- It runs on the server it watches: if the whole server stops, nothing reports it. A check from
  outside (a free external service) is the second layer and is not set up.
- It sends no messages yet. The state is only visible in the web page. A channel (Telegram or mail)
  is added later, and the monitors above already carry the certificate expiry check.
- Monitors and the administrator are not in Git and are not backed up automatically.
