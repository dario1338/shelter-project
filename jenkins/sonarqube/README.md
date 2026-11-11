# SonarQube as code

SonarQube (Community Build) and its PostgreSQL database, described as Compose files. Jenkins
reaches it as `http://sonarqube:9000` over the Docker network `shelter-ci-network`
(`jenkins/controller/casc/20-sonarqube.yaml`).

| Part | Where | Tool |
|---|---|---|
| Server and database (images pinned, volumes, network, restart policy) | `compose.yaml` | Docker Compose |
| Production network (already exists, shared with Jenkins) | `compose.production.yaml` | Docker Compose |
| Database password | file outside Git, mounted at `/run/secrets/sonar_db_password` | Compose secret |
| Projects, users, tokens, history of analyses | PostgreSQL database (volume `shelter_sonarqube_db`) | backup with `pg_dump` |

What is **not** in code: the contents of the database. Projects (`shelter-api`, `shelter-ui`) are
created by the first analysis, the Quality Gate and the quality profiles are the built-in ones, and
the administrator and the Jenkins token live in the database. Everything else can be rebuilt from
these files, and the database from a dump (see "Restore").

## Rules

- Version bumps are manual and always follow a backup. A newer SonarQube migrates the database
  when it starts and that cannot be undone, so the tag is pinned and Dependabot is not used here.
- The database password is a file, not a value in `compose.yaml` or `.env`. The SonarQube image
  has no `SONAR_JDBC_PASSWORD_FILE` (SonarSource: not supported yet), so the entrypoint in
  `compose.yaml` reads the file and exports the password for the SonarQube process only. It is not in
  `docker inspect`; it is visible to processes inside that container, and the file itself is not
  encrypted on disk.
- Never run `down -v` on production: it deletes the database volume.

## Files that are not in Git

`.env` (copy `.env.example`; on the server it only needs `SECRETS_DIR`) and one secret file,
**without a trailing newline**: `sonar_db_password`. SonarQube (uid 1000) and PostgreSQL (root, in its
entrypoint) must be able to read it: `chown 1000:1000` and `chmod 400`.

## Test on a laptop (from a dump of the real database)

Proves that the Compose files start a working SonarQube and that the dump can be restored. Nothing
real is touched: separate names, port 19000, test password. The test starts from the dump
that was made on the server (`pg_dump -Fc`).

```powershell
cd jenkins\sonarqube
New-Item -ItemType Directory secrets | Out-Null
'test-password-123' | Set-Content -NoNewline -Encoding ascii secrets\sonar_db_password
Copy-Item .env.example .env       # then uncomment the test overrides in .env
docker compose config             # check: test names, port 127.0.0.1:19000
docker compose up -d sonarqube-db
docker compose ps                 # wait until sonarqube-db is "healthy"
```

Copy the dump next to the compose file (e.g. `sonarqube-db.dump`; `*.dump` is in `.gitignore`) and restore it
into the empty database **before** SonarQube starts:

```powershell
docker compose cp sonarqube-db.dump sonarqube-db:/tmp/sonar.dump
docker compose exec sonarqube-db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-acl --single-transaction --exit-on-error /tmp/sonar.dump && rm /tmp/sonar.dump'
docker compose up -d sonarqube
docker compose logs -f sonarqube  # wait for "SonarQube is operational", Ctrl+C
```

`--no-owner --no-acl` make the restored objects belong to the new database user, so the dump does not depend on the
role names of the old database. `--single-transaction --exit-on-error` make a failed restore fail loudly instead of
leaving a half-filled database. Starting takes a few minutes (the search index is rebuilt).

Check at http://localhost:19000 (the administrator of the real SonarQube): login works, the projects
`shelter-api` and `shelter-ui` exist with their history, and
`docker compose exec sonarqube id` shows uid 1000. The password must not appear in:

```powershell
docker inspect sonarqube-test --format '{{json .Config.Env}}'
```

(only `SONAR_JDBC_URL` and `SONAR_JDBC_USERNAME`). Remove the test completely: `docker compose down -v`, then delete the
`secrets` directory, `.env` and the dump (it contains password hashes and token hashes).

## Migration of production

Run as root on the server, from `/opt/shelter-jenkins/repo/jenkins/sonarqube`, every command starting with
`docker compose -f compose.yaml -f compose.production.yaml`. The old containers `sonarqube` and
`sonarqube-db` (and their volumes `sonarqube_pg_*`, `sonarqube-db-data`) stay as the way back.

Preparation (no downtime):

1. Update the clone (`git pull`) and make a dump of the running database (it must be readable, see the
   command in "Backup").
2. Create the secret without printing it: `openssl rand -base64 33 | tr -d '\n' > /opt/shelter-jenkins/secrets/sonar_db_password`,
   then `chown 1000:1000` and `chmod 400`. Create `.env` (`chmod 600`) with `SECRETS_DIR=/opt/shelter-jenkins/secrets`.
3. `config` must show the production names (`sonarqube`, `sonarqube-db`, network `shelter-ci-network`, external) and port 9000.

Switch (a few minutes of downtime; no build running, no delivery waiting):

4. Stop SonarQube only (`docker stop sonarqube`), then take the final dump of the database, which is still running
   (`pg_dump`, see "Backup"); this is the data that is restored, and the rollback. Check it with `pg_restore -l`.
5. Stop the old database, set `docker update --restart=no sonarqube sonarqube-db`, and rename both
   (`docker rename sonarqube sonarqube-before-code`, same for `sonarqube-db`).
6. Start the new database, wait until it is healthy, restore the dump (same commands as in the laptop test, with the
   server's compose command), then start SonarQube.
7. Check: `curl -s http://127.0.0.1:9000/api/system/status` shows `"status":"UP"`; login and the history in the web
   interface; one CI build with both SonarQube stages (a pull request that touches `shelter-api` and `shelter-ui`)
   and its Quality Gate.

Rollback: `docker compose ... down` (without `-v`), `docker rename` the old containers back,
`docker update --restart=unless-stopped` both, `docker start sonarqube-db sonarqube`. The Jenkins token is in the
database, so Jenkins needs no change in either direction.

Only after a few days without problems: remove the `*-before-code` containers and the old volumes.

## Backup

The database is the only state. Dump it while SonarQube is running (it is a consistent snapshot):

```bash
umask 077
docker exec sonarqube-db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > /root/backups/sonarqube-db-$(date +%F).dump
docker exec -i sonarqube-db pg_restore -l < /root/backups/sonarqube-db-$(date +%F).dump | grep -c 'TABLE DATA'   # > 0
```

The dump contains password and token hashes: keep it `chmod 600`. Today it is only on the same server; a copy
elsewhere is a known gap.

## Known limits

- Jenkins and SonarQube are served over HTTP (port 9000 is open); HTTPS and narrowing the ports is a separate step.
- No health check on the SonarQube container (the image's tools are not guaranteed); use the status request above.
- The dump is not tested against a clean restore on the production server before the switch; the laptop test is the proof.
