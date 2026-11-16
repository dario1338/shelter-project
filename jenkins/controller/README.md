# Jenkins as code

The Jenkins controller, its build agent, its configuration, its credentials (as references) and
its jobs, described as files in this repository. With these files and the secret files, the
whole setup can be rebuilt on an empty machine.

| Part | Where | Tool |
|---|---|---|
| Controller image (base image, plugins, configuration) | `Dockerfile`, `plugins.txt`, `casc/` | Docker |
| Settings, security, agent node, SonarQube, credentials | `casc/*.yaml` | Jenkins Configuration as Code (JCasC) |
| Jobs (3 delivery, 3 rollback, 1 multibranch CI) | `casc/jobs/shelter.groovy` | Job DSL |
| Running it (controller + agent) | `compose.yaml`, `.env` | Docker Compose |
| Secrets | files outside Git, mounted at `/run/secrets` | Docker Compose secrets |

SonarQube and its database are not part of this yet; they keep running as before on the same
Docker network.

## Rules

- Change the configuration in the files, not in the Jenkins UI. A UI change is overwritten the next
  time the configuration is loaded. (Jenkins recommends managing a controller either by JCasC or by
  the UI, not both.)
- No secret in Git, in an image, or in an environment variable. Secrets are files.
- The jobs' pipelines stay in the `Jenkinsfile*` files; the Job DSL script only creates the jobs.
- Job parameters are declared in the Job DSL script and in the Jenkinsfiles. If you change one,
  change both.

## What is loaded when

Jenkins reads `casc/*.yaml` (baked into the image at `/usr/share/jenkins/casc`) at every start, and
again on *Manage Jenkins > Configuration as Code > Reload configuration*. Job
DSL regenerates the job definitions each time; build history is kept. Builds, job history,
users' API tokens and plugin data stay in the `jenkins_home` volume and are not in Git.
Because the files are baked into the image, a change takes effect by rebuilding the image and
recreating the container.

## Files that are not in Git

`.env` (copy `.env.example`, no secrets in it) and a directory of secret files (default
`./secrets`; on the server keep it outside the repository, e.g. `/opt/shelter-jenkins/secrets`,
set with `SECRETS_DIR`). One file per secret, **no trailing newline**:

| File | Content |
|---|---|
| `jenkins_admin_password` | password of the Jenkins administrator |
| `github_token` | GitHub token (credential `github-credentials`) |
| `dockerhub_token` | Docker Hub access token (credential `dockerhub-credentials`) |
| `sonarqube_token` | SonarQube token (credential `sonarqube-token`) |
| `render_api_key` | Render API key (credential `render-api-key`) |
| `cosign_key` | encrypted private key that signs the images (credential `cosign-key`), the file `cosign.key` as `cosign generate-key-pair` wrote it |
| `cosign_password` | its password (credential `cosign-password`) |
| `jenkins_agent_key` | private SSH key of the controller for the agent (credential `jenkins-agent-key`) |

On Linux the files must be readable by the user inside the container (uid 1000):
`sudo chown 1000:1000 secrets/* && sudo chmod 400 secrets/*`.

## Test 1: from scratch on a laptop (proves reproducibility)

Uses fake secrets and an empty volume; it touches nothing real. The agent is not started (the
agent image is arm64, the laptop is x86_64), so the node `docker-agent-1` shows as offline and the builds that the multibranch scan queues wait for it (they disappear with `down -v`).

```powershell
cd jenkins\controller
New-Item -ItemType Directory secrets | Out-Null
'test-password-123' | Set-Content -NoNewline -Encoding ascii secrets\jenkins_admin_password
foreach ($n in 'github_token','dockerhub_token','sonarqube_token','render_api_key','cosign_key','cosign_password','jenkins_agent_key') {
  'fake-value' | Set-Content -NoNewline -Encoding ascii "secrets\$n" }
Copy-Item .env.example .env   # then edit .env: see below
```

`.env` for the test: `JENKINS_ADMIN_ID=admin`, `SHELTER_JENKINS_URL=http://localhost:18080/`,
`DOCKERHUB_USERNAME=test`, `DOCKER_GID=999`, `JENKINS_AGENT_SSH_PUBKEY=ssh-ed25519 AAAA test`,
and the test overrides: `JENKINS_BIND=127.0.0.1`, `JENKINS_PORT=18080`,
`JENKINS_CONTAINER=jenkins-test`, `JENKINS_HOME_VOLUME=jenkins_home_test`,
`AGENT_HOME_VOLUME=jenkins-agent-test-home`, `CI_NETWORK=shelter-ci-test-network`.

```powershell
docker compose build jenkins
docker compose up -d jenkins
docker compose logs -f jenkins        # wait for "Jenkins is fully up and running", Ctrl+C
```

Check at http://localhost:18080 (user `admin`, password from the file): login works; 7 jobs exist;
`shelter-cd` shows the parameters *GIT_COMMIT_FULL*, *IMAGE_DIGEST* and *TEST_ROLLBACK* without having been run;
Credentials lists the 7 ids; the node `docker-agent-1` exists; *Configuration as Code > Reload
existing configuration* succeeds twice (idempotent). Then:

```powershell
docker port jenkins-test                       # only 8080/tcp, no 50000
docker exec jenkins-test ls -l /run/secrets    # six files
docker compose down -v                         # remove the test completely
```

## Test 2: a copy of the real data on the server (proves migration)

Restore the newest `jenkins_home` backup into a separate volume and start a controller from this
image on `127.0.0.1:18080` with a separate Compose project (`-p`), `JENKINS_HOME_VOLUME=jenkins_home_copy`,
different container and network names, no agent, and secret files with dummy values. The copy
contains the real credentials until Jenkins replaces them from the secret files, so keep it on the
server and delete it when done (`docker volume rm`, never `down -v`). Compare the number of builds
per job with production (`ls /var/jenkins_home/jobs/<job>/builds`), the plugin versions
(only `job-dsl` may be added), and look at the jobs through an SSH tunnel.

## Requirements on the server

Docker with the Compose plugin v2. On Ubuntu with the distribution's Docker the plugin is a
separate package: `apt-get install docker-compose-v2` (check first with `apt-get install -s`
that it only installs that package and upgrades nothing, because a Docker upgrade restarts the
daemon and with it every container).

To look at a test controller that listens only on `127.0.0.1`, use an SSH tunnel from the
laptop: `ssh -i <key> -L 18080:127.0.0.1:18080 ubuntu@<server>`, then open `http://localhost:18080`.
Jenkins warns about a "broken reverse proxy" when the address in the browser differs from
`SHELTER_JENKINS_URL`; for a tunnelled test that is expected.

## Migration of production

Production uses the volumes and the network that already exist, so it is started with the
override `compose.production.yaml` (they are `external`: Compose never creates, changes or
deletes them). Every command below is run as root on the server, from
`/opt/shelter-jenkins/repo/jenkins/controller`, and starts with
`docker compose -f compose.yaml -f compose.production.yaml`.

Preparation (no downtime):

1. Clone the repository to `/opt/shelter-jenkins/repo`; create `/opt/shelter-jenkins/secrets`.
2. Create the secret files without leaving the values in the shell history (`read -rs`), the SSH
   key pair with `ssh-keygen -t ed25519 -N "" -C jenkins-controller -f jenkins_agent_key`, then
   `chown 1000:1000` and `chmod 400` on all of them.
3. Create `.env` (`chmod 600`): no test overrides, `SECRETS_DIR=/opt/shelter-jenkins/secrets`,
   `JENKINS_AGENT_SSH_PUBKEY` from `jenkins_agent_key.pub`.
4. `config` must show the production names (`jenkins`, `jenkins-agent-1`, `jenkins_home`,
   `shelter-ci-network`) and only port 8080. Then `build jenkins`.

Switch (a few minutes of downtime):

5. Jenkins idle: no build running, no delivery waiting for approval.
6. Stop `jenkins` and `jenkins-agent-1`. Take a fresh backup of `jenkins_home` and test it
   (`gzip -t`). **This backup is the rollback.**
7. Rename the stopped containers (`docker rename jenkins jenkins-before-code`, same for the
   agent), then `up -d`. Port 50000 is no longer published.
8. Check: login, node `docker-agent-1` online, `authorized_keys` of the agent contains only the
   new key, one CI build (SonarQube, Docker Hub push), one delivery up to the approval step,
   and the multibranch scan log shows a GitHub quota of 5000 (a token is in use).

Rollback: stop the new containers and restore the backup into `jenkins_home`, then start the
renamed ones (`docker rename` back). Only after a few days without problems: remove the renamed
containers, revoke the old tokens, delete the leftover job `agent-smoke-test`.

## Updating plugins

`plugins.txt` pins every plugin. Dependabot does not read it. To update: in a test controller run
`jenkins-plugin-cli --plugin-file plugins.txt --available-updates`, change the versions, rebuild,
run Test 1, then commit. After any change, compare with the complete resolved list:
`docker run --rm shelter-jenkins-controller:<tag> jenkins-plugin-cli --list`.

## Known limits

- The JSON schema that Jenkins exports describes the `jobs:` key in a way that rejects the normal list form (`jobs: - file: ...`), so `casc/40-jobs.yaml` cannot be validated against it. The other files can. The proof for `jobs:` is that Jenkins starts and creates the jobs (Test 1).

- Plugins already in the `jenkins_home` volume are not replaced by the image's copies (the image
  only adds files that are missing). The pinned versions equal the installed ones, so nothing differs
  today; after a plugin update, the volume must be updated too (documented in Test 2 and cutover).
- Jenkins is reached over HTTPS through Caddy (`jenkins/caddy`) and listens only on `127.0.0.1` (`JENKINS_BIND`). It stays
  reachable from the internet through Caddy because GitHub has to reach the webhook, so it must be kept up to date.
- The agent has access to the host's Docker socket (needed for builds), so whoever can change a
  Jenkinsfile that runs there can control the server. That is why fork pull requests are not built.
- Job DSL: removed jobs are not deleted; the plugin is currently "up for adoption" (maintenance risk).
