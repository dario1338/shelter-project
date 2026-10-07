// Shared helpers for Jenkinsfile.cd and Jenkinsfile.rollback.
// Loaded with `load 'jenkins/render.groovy'`; call init() once after loading.
// All functions expect RENDER_KEY to be bound by the caller (withCredentials).

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

// Waits until the service answers /api/health with 2xx. Also wakes up a sleeping free-tier instance.
def waitHealthy(String baseUrl) {
    timeout(time: 10, unit: 'MINUTES') {
        waitUntil(initialRecurrencePeriod: 10000) {
            def code = sh(returnStatus: true,
                          script: "curl -sS -f --max-time 60 '${baseUrl}/api/health' -o /dev/null")
            if (code != 0) {
                echo "${baseUrl} not healthy yet (curl exit code ${code}), retrying"
                return false
            }
            return true
        }
    }
}

// Probes /api/health directly on a service, waking it up if it sleeps (a cold start takes about 95 seconds).
// Returns true/false and never fails the build, so it can be used where an unhealthy service is a valid state.
def probeHealthy(String baseUrl, int maxAttempts) {
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        def code = sh(returnStatus: true,
                      script: "curl -sS -f --max-time 60 '${baseUrl}/api/health' -o /dev/null")
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
//   - 401/403/404: a version older than /api/info (anonymous requests to unknown paths are denied)
//   - the revision is 'unknown'
// Any other outcome (timeout, 5xx, sleeping instance) is retried and then fails the build,
// so a transient error is never mistaken for "no revision".
def fetchRevision(String baseUrl) {
    for (int attempt = 1; attempt <= 6; attempt++) {
        def code = sh(returnStdout: true, script: """
            curl -sS -o info-probe.json -w '%{http_code}' --max-time 60 '${baseUrl}/api/info' || true
        """).trim()
        if (code in ['401', '403', '404']) {
            return ''
        }
        if (code ==~ /2\d\d/) {
            def revision = readJSON(file: 'info-probe.json').revision
            return (revision && revision != 'unknown') ? revision : ''
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
                          script: "curl -sS -f --max-time 30 '${baseUrl}/api/info' -o info.json")
            if (code != 0) {
                echo "Service not responding yet (curl exit code ${code}), retrying"
                return false
            }
            def revision = readJSON(file: 'info.json').revision
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

// Asks the proxy which instance serves production traffic and sets ACTIVE_HOST, IDLE_HOST, IDLE_ID.
def resolveInstances() {
    def api = servedBy(env.PROXY_URL, '/api/health')
    def ui  = servedBy(env.PROXY_URL, '/')
    echo "Proxy serves the API from '${api}' and the UI from '${ui}'"
    if (!api || api != ui) {
        error "Cannot determine the active instance (api='${api}', ui='${ui}')"
    }
    if (api == env.BLUE_HOST) {
        env.ACTIVE_HOST = env.BLUE_HOST
        env.IDLE_HOST   = env.GREEN_HOST
        env.IDLE_ID     = env.GREEN_ID
    } else if (api == env.GREEN_HOST) {
        env.ACTIVE_HOST = env.GREEN_HOST
        env.IDLE_HOST   = env.BLUE_HOST
        env.IDLE_ID     = env.BLUE_ID
    } else {
        error "Unknown upstream '${api}'"
    }
    echo "Active: ${env.ACTIVE_HOST}, idle: ${env.IDLE_HOST}"
}

def setUpstream(String host) {
    for (key in ['API_UPSTREAM', 'UI_UPSTREAM']) {
        def code = sh(returnStdout: true, script: """
            curl -sS -o env-response.json -w '%{http_code}' \
              -X PUT "${env.RENDER_API}/services/${env.PROXY_ID}/env-vars/${key}" \
              -H "Authorization: Bearer \$RENDER_KEY" \
              -H "Content-Type: application/json" \
              -d '{"value": "${host}"}'
        """).trim()
        if (!(code ==~ /2\d\d/)) {
            error "Failed to set ${key} (HTTP ${code}): ${readFile('env-response.json')}"
        }
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
                          script: "curl -sS -f --max-time 60 -D headers.txt -o info.json '${env.PROXY_URL}/api/info'")
            if (code != 0) {
                echo "Proxy not answering correctly yet (curl exit code ${code}), retrying"
                return false
            }
            def revision = readJSON(file: 'info.json').revision
            def host = sh(returnStdout: true, script: "grep -i '^x-served-by:' headers.txt | head -1 | awk '{print \$2}' | tr -d '\\r' || true").trim()
            echo "Proxy reports revision ${revision} served by ${host}; expected ${expectedRevision} on ${expectedHost}"
            return revision == expectedRevision && host == expectedHost
        }
    }
}

// Checks only the routing. Used when the target version may not have /api/info.
def verifyServedBy(String expectedHost) {
    timeout(time: 5, unit: 'MINUTES') {
        waitUntil(initialRecurrencePeriod: 10000) {
            def host = servedBy(env.PROXY_URL, '/api/health')
            echo "Proxy serves the API from '${host}', expected '${expectedHost}'"
            return host == expectedHost
        }
    }
}

return this