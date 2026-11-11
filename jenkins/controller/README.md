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
foreach ($n in 'github_token','dockerhub_token','sonarqube_token','render_api_key','jenkins_agent_key') {
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
`shelter-cd` shows the parameters *GIT_COMMIT_FULL* and *TEST_ROLLBACK* without having been run;
Credentials lists the 5 ids; the node `docker-agent-1` exists; *Configuration as Code > Reload
existing configuration* succeeds twice (idempotent). Then:

```powershell
docker port jenkins-test                       # only 8080/tcp, no 50000
docker exec jenkins-test ls -l /run/secrets    # six files
docker compose down -v                         # remove the test completely
```

## Test 2: a copy of the real data on the server (proves migration)

Restore the newest `jenkins_home` backup into a separate volume and start a controller from this
image on `127.0.0.1:18080` with `JENKINS_HOME_VOLUME=jenkins_home_copy`, a different container
name, no agent, and secret files with dummy values (the copy contains the real credentials; keep it
on the server and delete it when done). Check that jobs, build history and parameters are intact.
The exact commands are given step by step when this test is run.

## Migration of production (summary)

1. Jenkins idle (no build, no delivery waiting for approval). Stop it. Take a fresh backup of
   `jenkins_home` (this backup is the rollback).
2. Create the real secret files and `.env` on the server. Generate a new SSH key pair for the
   agent (`ssh-keygen -t ed25519 -f <dir>/jenkins_agent_key -N ""`); its `.pub` goes to
   `JENKINS_AGENT_SSH_PUBKEY`.
3. Rename the old containers (`jenkins`, `jenkins-agent-1`), then `docker compose up -d --build`.
   Same volumes, same network, same names; port 50000 is no longer published.
4. Check: login, node online, Sonar reachable, one CI build, one CD run.

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
- Jenkins and SonarQube are served over HTTP; HTTPS is a separate step.
- The agent has access to the host's Docker socket (needed for builds), so whoever can change a
  Jenkinsfile that runs there can control the server. That is why fork pull requests are not built.
- Job DSL: removed jobs are not deleted; the plugin is currently "up for adoption" (maintenance risk).
