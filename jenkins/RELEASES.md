# Release records

Every successful production release is recorded, so that a rollback can deploy the exact image that ran
before. An image tag can be moved to other content; a digest cannot.

| What | Where |
|---|---|
| Records | branch `releases`, one file per component: `releases/shelter-api.json`, `shelter-ui.json`, `shelter-proxy.json` |
| Entry | `commit` (40 characters), `digest` (`sha256:...`), `deployedAt` (UTC), `buildUrl`, `previousDigest` |
| Written by | the delivery pipelines (`Jenkinsfile.cd*`), after the production release and its verification, with the deploy key (credential `releases-deploy-key`) |
| Read by | the rollback pipelines and the automatic rollback of the UI and proxy delivery (`render.imageForTag`), anonymously (public repository) |
| Code | `jenkins/render.groovy` (`recordRelease`, `imageForTag`), `jenkins/release-record.js` |

Rules and limits:

- The branch is protected against deletion and force push (ruleset `releases-protection`). The deploy key
  can push to any branch that is not protected, but not to `main` (its ruleset has no bypass).
- If the record cannot be written, the release stays live, the build becomes UNSTABLE and the record is kept
  as a build artifact (`release-record-<component>.json`), so it can be added by hand.
- A release delivered by tag (a manual run without `IMAGE_DIGEST`) is not recorded.
- A rollback to a release without a record falls back to the tag and says so in the log (WARNING).
- A digest only works while the image exists on Docker Hub; do not delete images of releases you may restore.
- The SSH host key of GitHub is pinned in `jenkins/github_known_hosts` (fingerprint
  `SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU`, as published by GitHub).
