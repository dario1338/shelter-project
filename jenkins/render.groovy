// Shared helpers of the delivery and rollback pipelines of the API, the UI and the proxy
// (Jenkinsfile.cd*, Jenkinsfile.rollback*). Loaded with `load 'jenkins/render.groovy'`; call init() once after loading.
// All functions expect RENDER_KEY to be bound by the caller (withCredentials), except serviceEnvVar(),
// proxyEnvVar(), resolveInstances() and smokeProxy(), which bind it themselves.

def init() {
    env.RENDER_API  = 'https://api.render.com/v1'
    env.IMAGE_REPO  = 'docker.io/dario1338/shelter-api'
    env.STAGING_ID  = 'srv-db0j781srm7s73fr95c0'
    env.STAGING_URL = 'https://shelter-api-staging.onrender.com'
    env.BLUE_ID     = 'srv-db0iv4142hec73e9n5p0'
    env.BLUE_HOST   = 'shelter-api-blue.onrender.com'
    env.GREEN_ID    = 'srv-db0jc5id0e5s73bt6lv0'
    env.GREEN_HOST  = 'shelter-api-green.onrender.com'
    env.PROXY_ID    = 'srv-db2fmnm7bikc73dkdtv0'
    env.PROXY_URL   = 'https://shelter-proxy-7gcs.onrender.com'

    // Settings of the component being delivered. The defaults describe the API.
    env.UPSTREAM_KEY   = 'API_UPSTREAM'   // proxy variable that points to the active instance
    env.HEALTH_PATH    = '/api/health'    // answers 2xx once the instance is up
    env.REVISION_PATH  = '/api/info'      // JSON with the "revision" field
    env.SERVED_BY_PATH = '/api/health'    // path the proxy forwards to this component
}

// Selects the component the helpers act on. init() selects the API.
// The UI is a static site without state, so it has one staging and one production service
// (no blue/green pair); the proxy variable UI_UPSTREAM points to the production service.
def useComponent(String name) {
    if (name == 'api') {
        init()
    } else if (name == 'ui') {
        env.IMAGE_REPO     = 'docker.io/dario1338/shelter-ui'
        env.STAGING_ID     = 'srv-dbljg70u01pc73ad0hrg'
        env.STAGING_URL    = 'https://shelter-ui-staging.onrender.com'
        env.PROD_ID        = 'srv-dbljhdajnfac73875ah0'
        env.PROD_HOST      = 'shelter-ui.onrender.com'
        env.PROD_URL       = 'https://shelter-ui.onrender.com'
        env.UPSTREAM_KEY   = 'UI_UPSTREAM'
        env.HEALTH_PATH    = '/healthz'
        env.REVISION_PATH  = '/version.json'
        env.SERVED_BY_PATH = '/'
    } else if (name == 'proxy') {
        // The proxy is stateless and has one staging and one production service. Both are reached
        // through their own address, so the "proxy" settings below describe the service itself.
        env.IMAGE_REPO     = 'docker.io/dario1338/shelter-proxy'
        env.STAGING_ID     = 'srv-dbpfil3m8hqs73dpc3og'
        env.STAGING_URL    = 'https://shelter-proxy-staging.onrender.com'
        env.PROD_ID        = env.PROXY_ID
        env.PROD_URL       = env.PROXY_URL
        env.HEALTH_PATH    = '/proxy-health'
        env.REVISION_PATH  = '/proxy-version'
    } else {
        error "Unknown component '${name}'"
    }
}

// Returns the "revision" field of a JSON file, or '' when the file is not JSON or has no usable
// revision. A UI image built before version.json existed answers with the HTML entry page instead.
def readRevision(String file) {
    // JSON starts with "{"; the HTML entry page does not. Checked first so that no failing step is shown.
    def looksLikeJson = sh(returnStatus: true, script: "head -c 1 '${file}' | grep -q '{'") == 0
    if (!looksLikeJson) {
        echo "${file} does not contain JSON"
        return ''
    }
    try {
        def revision = readJSON(file: file).revision
        if (!revision || revision.toString() in ['unknown', 'null']) {
            return ''
        }
        return revision.toString()
    } catch (e) {
        echo "${file} does not contain valid JSON (${e.getMessage()})"
        return ''
    }
}

def startDeploy(String serviceId, String jsonBody) {
    def code = sh(returnStdout: true, script: """
        curl -sS -o deploy-response.json -w '%{http_code}' \
          -X POST "${env.RENDER_API}/services/${serviceId}/deploys" \
          -H "Authorization: Bearer \$RENDER_KEY" \
          -H "Content-Type: application/json" \
          -d '${jsonBody}'
    """).trim()
    def body = readFile('deploy-response.json')
    if (!(code ==~ /2\d\d/)) {
        error "Render rejected the deploy request (HTTP ${code}): ${body}"
    }
    def deployId = readJSON(text: body).id
    echo "Deploy ${deployId} started for service ${serviceId}"
    return deployId
}

def deployImage(String serviceId, String imageUrl) {
    return startDeploy(serviceId, "{\"imageUrl\": \"${imageUrl}\"}")
}

// ---------------------------------------------------------------- Release records
// Every successful production release (and every successful rollback) archives a small record with its
// build: commit and image digest (release-record-<component>.json, see jenkins/release-record.js).
// A rollback names an earlier successful delivery build, copies the record from it and deploys that
// digest, so it never trusts an image tag that can be moved. A build that did not finish a successful
// production release has no record and cannot be chosen. These functions need the repository checked out
// (jenkins/release-record.js) and node.

def componentName() {
    return env.IMAGE_REPO.substring(env.IMAGE_REPO.lastIndexOf('/') + 1)
}

def recordFile() {
    return "release-record-${componentName()}.json"
}

// Delivery job (the build history that holds the releases) and rollback job of the current component.
def deliveryJob() {
    return ['shelter-api': '/shelter-cd', 'shelter-ui': '/shelter-cd-ui', 'shelter-proxy': '/shelter-cd-proxy'][componentName()]
}

def rollbackJob() {
    return ['shelter-api': '/shelter-rollback', 'shelter-ui': '/shelter-rollback-ui', 'shelter-proxy': '/shelter-rollback-proxy'][componentName()]
}

def readRecord(String file) {
    def commit = sh(returnStdout: true, script: "node jenkins/release-record.js get ${file} commit").trim()
    def digest = sh(returnStdout: true, script: "node jenkins/release-record.js get ${file} digest").trim()
    return [commit: commit, digest: digest]
}

// The release made by delivery build number N: [commit: <40 characters>, digest: sha256:...].
def releaseFromBuild(String number) {
    def dir = 'record-source'
    sh "rm -rf ${dir}"
    try {
        copyArtifacts(projectName: deliveryJob(), selector: specific(number), filter: recordFile(), target: dir, fingerprintArtifacts: true)
    } catch (e) {
        error "Build #${number} of ${deliveryJob()} has no release record (${e.getMessage()}). Only a build that completed a successful production release can be chosen, and its record must still be kept."
    }
    def record = readRecord("${dir}/${recordFile()}")
    sh "rm -rf ${dir}"
    return record
}

// Image reference for the version with this revision (40 characters, or a prefix): by digest when the
// newest successful delivery or rollback build recorded exactly that version, otherwise by tag
// (weaker; a WARNING says so). Used to restore what ran before when a release or a rollback fails.
def imageForRevision(String revision) {
    if (revision && revision.length() >= 7 && revision != 'unknown') {
        for (job in [deliveryJob(), rollbackJob()]) {
            try {
                sh 'rm -rf record-source'
                copyArtifacts(projectName: job, selector: lastSuccessful(), filter: recordFile(), target: 'record-source', optional: true)
                if (fileExists("record-source/${recordFile()}")) {
                    def record = readRecord("record-source/${recordFile()}")
                    if (record.commit.startsWith(revision)) {
                        echo "Version ${revision.take(7)} was recorded by the last successful ${job} build, using its digest ${record.digest}"
                        return "${env.IMAGE_REPO}@${record.digest}"
                    }
                }
            } catch (e) {
                echo "WARNING: could not read the record of ${job} (${e.getMessage()})."
            } finally {
                sh 'rm -rf record-source'
            }
        }
    }
    echo "WARNING: no record for ${revision?.take(7)}, so the image is taken by tag, which is weaker than by digest."
    return "${env.IMAGE_REPO}:${revision.take(7)}"
}

// The digest part of an image reference such as repo@sha256:..., or '' for a reference by tag.
def digestOf(String image) {
    return image.contains('@') ? image.substring(image.indexOf('@') + 1) : ''
}

// Archives the record of this release (or rollback) with the build. It is already live when this runs, so
// a failure here never rolls anything back: the build only becomes UNSTABLE.
def recordRelease(String commit, String digest, String action = 'release') {
    if (!digest || !commit) {
        echo "WARNING: this ${action} has no digest or no commit (it was done by tag or the version does not report a revision), so it is not recorded."
        return
    }
    try {
        def stamp = sh(returnStdout: true, script: 'date -u +%Y-%m-%dT%H:%M:%SZ').trim()
        def file = recordFile()
        withEnv(["REC_COMMIT=${commit}", "REC_DIGEST=${digest}", "REC_TIME=${stamp}", "REC_URL=${env.BUILD_URL}", "REC_FILE=${file}", "REC_ACTION=${action}"]) {
            sh 'node jenkins/release-record.js entry "$REC_COMMIT" "$REC_DIGEST" "$REC_TIME" "$REC_URL" "$REC_ACTION" > "$REC_FILE"'
        }
        archiveArtifacts artifacts: file, fingerprint: true
        echo "Recorded ${componentName()} ${action} ${commit.take(7)} (${digest}) with build #${env.BUILD_NUMBER}"
    } catch (e) {
        echo "WARNING: the ${action} is live but could not be recorded: ${e.getMessage()}"
        unstable("The ${action} is live, but its record could not be archived")
    }
}

def waitForDeploy(String serviceId, String deployId) {
    timeout(time: 15, unit: 'MINUTES') {
        waitUntil(initialRecurrencePeriod: 10000) {
            def out = sh(returnStdout: true, script: """
                curl -sS -f "${env.RENDER_API}/services/${serviceId}/deploys/${deployId}" \
                  -H "Authorization: Bearer \$RENDER_KEY"
            """).trim()
            def status = readJSON(text: out).status
            echo "Deploy ${deployId}: ${status}"
            if (status in ['build_failed', 'update_failed', 'pre_deploy_failed', 'canceled', 'deactivated']) {
                error "Deploy ${deployId} failed with status: ${status}"
            }
            return status == 'live'
        }
    }
}

// Waits until the service answers its health path with 2xx. Also wakes up a sleeping free-tier instance.
def waitHealthy(String baseUrl) {
    timeout(time: 10, unit: 'MINUTES') {
        waitUntil(initialRecurrencePeriod: 10000) {
            def code = sh(returnStatus: true,
                          script: "curl -sS -f --max-time 60 '${baseUrl}${env.HEALTH_PATH}' -o /dev/null")
            if (code != 0) {
                echo "${baseUrl} not healthy yet (curl exit code ${code}), retrying"
                return false
            }
            return true
        }
    }
}

// Probes the health path directly on a service, waking it up if it sleeps (a cold start takes about 95 seconds).
// Returns true/false and never fails the build, so it can be used where an unhealthy service is a valid state.
def probeHealthy(String baseUrl, int maxAttempts) {
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        def code = sh(returnStatus: true,
                      script: "curl -sS -f --max-time 60 '${baseUrl}${env.HEALTH_PATH}' -o /dev/null")
        if (code == 0) {
            return true
        }
        echo "${baseUrl} not healthy yet (curl exit code ${code}), attempt ${attempt} of ${maxAttempts}"
        sleep 10
    }
    return false
}

// Returns the full commit hash the service reports.
// Returns '' only when the service clearly answers WITHOUT a usable revision:
//   - 401/403/404: a version older than the revision endpoint (anonymous requests to unknown paths are denied)
//   - a body that is not JSON, or a revision that is 'unknown'
// Any other outcome (timeout, 5xx, sleeping instance) is retried and then fails the build,
// so a transient error is never mistaken for "no revision".
def fetchRevision(String baseUrl) {
    for (int attempt = 1; attempt <= 6; attempt++) {
        def code = sh(returnStdout: true, script: """
            curl -sS -o info-probe.json -w '%{http_code}' --max-time 60 '${baseUrl}${env.REVISION_PATH}' || true
        """).trim()
        if (code in ['401', '403', '404']) {
            return ''
        }
        if (code ==~ /2\d\d/) {
            return readRevision('info-probe.json')
        }
        echo "Could not read the revision from ${baseUrl} (HTTP ${code}), attempt ${attempt} of 6"
        sleep 15
    }
    error "Could not determine the revision of ${baseUrl}: the service did not answer correctly"
}

def verifyRevision(String baseUrl, String expected) {
    timeout(time: 10, unit: 'MINUTES') {
        waitUntil(initialRecurrencePeriod: 10000) {
            def code = sh(returnStatus: true,
                          script: "curl -sS -f --max-time 30 '${baseUrl}${env.REVISION_PATH}' -o info.json")
            if (code != 0) {
                echo "Service not responding yet (curl exit code ${code}), retrying"
                return false
            }
            def revision = readRevision('info.json')
            echo "Reported revision: ${revision}, expected: ${expected}"
            return revision == expected
        }
    }
}

// Reads the X-Served-By header the proxy adds to every response (also to error responses).
def servedBy(String baseUrl, String path) {
    return sh(returnStdout: true, script: """
        curl -sS -o /dev/null -D - --max-time 200 "${baseUrl}${path}" \
          | grep -i '^x-served-by:' | head -1 | awk '{print \$2}' | tr -d '\\r' || true
    """).trim()
}

// Returns the value of an environment variable of a service, read from the Render API.
def serviceEnvVar(String serviceId, String key) {
    def value = null
    withCredentials([string(credentialsId: 'render-api-key', variable: 'RENDER_KEY')]) {
        def out = sh(returnStdout: true, script: """
            curl -sS -f "${env.RENDER_API}/services/${serviceId}/env-vars?limit=100" \
              -H "Authorization: Bearer \$RENDER_KEY"
        """).trim()
        for (entry in readJSON(text: out)) {
            def item = entry.envVar ?: entry
            if (item.key == key) {
                value = item.value
            }
        }
    }
    if (!value) {
        error "The service ${serviceId} has no environment variable ${key}"
    }
    return value.toString()
}

// The same for the production proxy.
def proxyEnvVar(String key) {
    return serviceEnvVar(env.PROXY_ID, key)
}

// Looks up which instance the proxy is configured to use for the current component and sets
// ACTIVE_HOST, IDLE_HOST and IDLE_ID. Only for components with a blue/green pair (the API).
// The configuration is read from the Render API, not from the responses of the running services:
// a free-tier instance that sleeps answers the proxy with 502 for up to two minutes, and the release
// used to fail on that before it had even started. The configured value is the one setUpstream changes,
// and it is only changed through a deploy that is waited for.
def resolveInstances() {
    def configured = proxyEnvVar(env.UPSTREAM_KEY)
    echo "The proxy is configured to serve ${env.UPSTREAM_KEY} from '${configured}'"
    if (configured == env.BLUE_HOST) {
        env.ACTIVE_HOST = env.BLUE_HOST
        env.IDLE_HOST   = env.GREEN_HOST
        env.IDLE_ID     = env.GREEN_ID
    } else if (configured == env.GREEN_HOST) {
        env.ACTIVE_HOST = env.GREEN_HOST
        env.IDLE_HOST   = env.BLUE_HOST
        env.IDLE_ID     = env.BLUE_ID
    } else {
        error "Unknown upstream '${configured}' for ${env.UPSTREAM_KEY}"
    }
    echo "Active: ${env.ACTIVE_HOST}, idle: ${env.IDLE_HOST}"
}

// Points the proxy of the current component to the given host and redeploys the proxy.
def setUpstream(String host) {
    def code = sh(returnStdout: true, script: """
        curl -sS -o env-response.json -w '%{http_code}' \
          -X PUT "${env.RENDER_API}/services/${env.PROXY_ID}/env-vars/${env.UPSTREAM_KEY}" \
          -H "Authorization: Bearer \$RENDER_KEY" \
          -H "Content-Type: application/json" \
          -d '{"value": "${host}"}'
    """).trim()
    if (!(code ==~ /2\d\d/)) {
        error "Failed to set ${env.UPSTREAM_KEY} (HTTP ${code}): ${readFile('env-response.json')}"
    }
    // Environment variable changes are not applied until the service is deployed.
    def deployId = startDeploy(env.PROXY_ID, '{}')
    waitForDeploy(env.PROXY_ID, deployId)
}

// Checks through the proxy that the expected revision is served by the expected instance.
def verifyThroughProxy(String expectedHost, String expectedRevision) {
    timeout(time: 10, unit: 'MINUTES') {
        waitUntil(initialRecurrencePeriod: 10000) {
            def code = sh(returnStatus: true,
                          script: "curl -sS -f --max-time 60 -D headers.txt -o info.json '${env.PROXY_URL}${env.REVISION_PATH}'")
            if (code != 0) {
                echo "Proxy not answering correctly yet (curl exit code ${code}), retrying"
                return false
            }
            def revision = readRevision('info.json')
            def host = sh(returnStdout: true, script: "grep -i '^x-served-by:' headers.txt | head -1 | awk '{print \$2}' | tr -d '\\r' || true").trim()
            echo "Proxy reports revision ${revision} served by ${host}; expected ${expectedRevision} on ${expectedHost}"
            return revision == expectedRevision && host == expectedHost
        }
    }
}

// Checks only the routing. Used when the target version may not report a revision.
def verifyServedBy(String expectedHost) {
    timeout(time: 5, unit: 'MINUTES') {
        waitUntil(initialRecurrencePeriod: 10000) {
            def host = servedBy(env.PROXY_URL, env.SERVED_BY_PATH)
            echo "Proxy serves ${env.SERVED_BY_PATH} from '${host}', expected '${expectedHost}'"
            return host == expectedHost
        }
    }
}

// Smoke test of the static UI: health endpoint, the entry page and a client-side route
// (the single-page-app fallback must answer unknown paths with the entry page).
def smokeUi(String baseUrl) {
    sh "curl -sS -f --max-time 60 '${baseUrl}/healthz' | grep -qx ok"
    sh "curl -sS -f --max-time 60 '${baseUrl}/' | grep -q 'id=\"root\"'"
    sh "curl -sS -f --max-time 60 '${baseUrl}/some/client/route' | grep -q 'id=\"root\"'"
    echo "UI smoke tests passed for ${baseUrl}"
}

// Waits until a service answers a path with 2xx, which also wakes it up if it sleeps. A sleeping free-tier
// service is woken only by a request made to its own address, not by the proxy that forwards to it, so a
// check made through a proxy has to wake what is behind it first.
def wakeHost(String host, String path) {
    timeout(time: 10, unit: 'MINUTES') {
        waitUntil(initialRecurrencePeriod: 10000) {
            def code = sh(returnStatus: true, script: "curl -sS -f --max-time 60 'https://${host}${path}' -o /dev/null")
            if (code != 0) {
                echo "${host}${path} not answering yet (curl exit code ${code}), retrying"
                return false
            }
            return true
        }
    }
}

// Wakes the API and the UI that a proxy forwards to, read from the configuration of its Render service.
// Needed before anything is asked of the proxy that reaches them, such as its revision (an old proxy
// forwards unknown paths to the UI).
def wakeUpstreams(String proxyServiceId) {
    wakeHost(serviceEnvVar(proxyServiceId, 'API_UPSTREAM'), '/api/health')
    wakeHost(serviceEnvVar(proxyServiceId, 'UI_UPSTREAM'), '/healthz')
}

// Smoke test of a proxy, given its address and the id of its Render service. The two services behind it
// are woken up, and then the proxy is checked from the outside: its health, which API instance it
// announces and serves, and that it serves the UI.
def smokeProxy(String baseUrl, String proxyServiceId) {
    def apiHost = serviceEnvVar(proxyServiceId, 'API_UPSTREAM')
    def uiHost = serviceEnvVar(proxyServiceId, 'UI_UPSTREAM')
    echo "The proxy ${baseUrl} serves the API from ${apiHost} and the UI from ${uiHost}"
    wakeUpstreams(proxyServiceId)

    sh "curl -sS -f --max-time 60 '${baseUrl}/proxy-health' | grep -qx ok"
    def announced = sh(returnStdout: true, script: "curl -sS -f --max-time 60 '${baseUrl}/api-host'").trim()
    if (announced != apiHost) {
        error "The proxy announces the API host '${announced}', expected '${apiHost}'"
    }
    sh "curl -sS -f --max-time 120 '${baseUrl}/api/health' -o /dev/null"
    def apiServedBy = servedBy(baseUrl, '/api/health')
    if (apiServedBy != apiHost) {
        error "The proxy serves the API from '${apiServedBy}', expected '${apiHost}'"
    }
    smokeUi(baseUrl)
    def uiServedBy = servedBy(baseUrl, '/')
    if (uiServedBy != uiHost) {
        error "The proxy serves the UI from '${uiServedBy}', expected '${uiHost}'"
    }
    echo "Proxy smoke tests passed for ${baseUrl}"
}

// Verifies that an image was signed by our CI key. The public key is in the repository (jenkins/cosign.pub),
// so the check needs nothing from Jenkins' secrets. Only digest references are accepted: a tag can be moved,
// and the signature is bound to the digest. The signatures are not recorded in a transparency log (see
// jenkins/SIGNING.md), hence --insecure-ignore-tlog. The reference goes through the environment, never
// into the shell script.
def verifySignature(String image) {
    if (!(image ==~ /^[a-z0-9.\/_-]+@sha256:[0-9a-f]{64}$/)) {
        error "Cannot verify '${image}': a reference with a digest (name@sha256:...) is required"
    }
    withEnv(["IMAGE_TO_VERIFY=${image}"]) {
        sh 'cosign verify --key jenkins/cosign.pub --insecure-ignore-tlog=true "${IMAGE_TO_VERIFY}" > /dev/null'
    }
    echo "Signature verified: ${image}"
}

return this
