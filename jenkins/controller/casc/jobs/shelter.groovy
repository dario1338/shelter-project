// Job DSL: all jobs of the shelter-project delivery system.
//
// Run by JCasC (casc/40-jobs.yaml) at every start and on "Reload existing configuration".
// Each run regenerates the job configuration from this file; build history is kept
// (same job name). Parameters are declared here AND in the Jenkinsfiles on purpose:
// a Declarative pipeline only registers its parameters after its first run, so without
// them here a freshly created job would show no parameters until someone started it.
//
// Jobs removed from this file are NOT deleted from Jenkins (delete them by hand).

def ghOwner = 'dario1338'
def ghRepo  = 'shelter-project'
def repoUrl   = "https://github.com/${ghOwner}/${ghRepo}.git"

// Same effect as options { disableConcurrentBuilds() } in the Jenkinsfiles (Job DSL has no
// method for it on pipeline jobs, so the job property is written to the job's XML directly,
// exactly as Jenkins stores it). Without it, a job regenerated at a restart would allow
// concurrent builds until its next run re-registers the option.
def noConcurrentBuilds = { node ->
    node / 'properties' / 'org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty' {
        abortPrevious(false)
    }
}

// Delivery pipelines (CD): started by hand, with the commit to deliver.
// The repository is public, so the checkout needs no credentials.
def deliveries = [
    [name: 'shelter-cd',       script: 'Jenkinsfile.cd',       what: 'API'],
    [name: 'shelter-cd-ui',    script: 'Jenkinsfile.cd.ui',    what: 'UI'],
    [name: 'shelter-cd-proxy', script: 'Jenkinsfile.cd.proxy', what: 'proxy'],
]
deliveries.each { d ->
    pipelineJob(d.name) {
        description("Delivery of the ${d.what}: staging, manual approval, production, automatic rollback on failure. Defined in ${d.script}.")
        configure noConcurrentBuilds
        parameters {
            stringParam('GIT_COMMIT_FULL', '', 'Full commit hash (40 characters) of the image to deliver')
            stringParam('IMAGE_DIGEST', '', 'Digest (sha256:...) of the image built by CI, set by CI. If empty (manual run), the image is taken by tag, which is weaker')
            booleanParam('TEST_ROLLBACK', false, 'TEST ONLY: fail the verification right after the traffic switch, to demonstrate the automatic rollback')
        }
        definition {
            cpsScm {
                scm {
                    git {
                        remote { url(repoUrl) }
                        branch('*/main')
                    }
                }
                scriptPath(d.script)
                lightweight(true)
            }
        }
    }
}

// Rollback pipelines: started by hand, with the number of an earlier successful delivery build to go back to.
def rollbacks = [
    [name: 'shelter-rollback',       script: 'Jenkinsfile.rollback',       what: 'API'],
    [name: 'shelter-rollback-ui',    script: 'Jenkinsfile.rollback.ui',    what: 'UI'],
    [name: 'shelter-rollback-proxy', script: 'Jenkinsfile.rollback.proxy', what: 'proxy'],
]
rollbacks.each { r ->
    pipelineJob(r.name) {
        description("Manual rollback of the ${r.what} to the release of an earlier delivery build. Defined in ${r.script}.")
        configure noConcurrentBuilds
        parameters {
            stringParam('TARGET_BUILD', '', 'Number of an earlier successful delivery build (shelter-cd, shelter-cd-ui, shelter-cd-proxy) to roll back to')
        }
        definition {
            cpsScm {
                scm {
                    git {
                        remote { url(repoUrl) }
                        branch('*/main')
                    }
                }
                scriptPath(r.script)
                lightweight(true)
            }
        }
    }
}

// CI: branches and pull requests of the repository (Jenkinsfile.ci).
// Only branches and pull requests from this repository are built. Pull requests from
// forks are deliberately NOT discovered: the agent has access to the Docker socket,
// so building code from a stranger's fork would let it run commands on the server.
multibranchPipelineJob('shelter-project') {
    branchSources {
        branchSource {
            source {
                github {
                    id('1')
                    repoOwner(ghOwner)
                    repository(ghRepo)
                    repositoryUrl("https://github.com/${ghOwner}/${ghRepo}")
                    configuredByUrl(false)
                    credentialsId('github-credentials')
                    traits {
                        gitHubBranchDiscovery { strategyId(1) }
                        gitHubPullRequestDiscovery { strategyId(2) }
                    }
                }
            }
        }
    }
    factory {
        workflowBranchProjectFactory { scriptPath('Jenkinsfile.ci') }
    }
    orphanedItemStrategy {
        discardOldItems { }
    }
    triggers {
        periodicFolderTrigger { interval('1d') }
    }
}
