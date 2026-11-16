# Release records and rollback

Every successful production release, and every successful rollback, archives a small record with its build:
the commit and the image digest. A rollback names an earlier successful delivery build, takes the digest from
that build's record and deploys it, so it never relies on an image tag that can be moved to other content.

| What | Where |
|---|---|
| Record | `release-record-<component>.json`, archived by the build (`archiveArtifacts`, with fingerprint) |
| Fields | `commit` (40 characters), `digest` (`sha256:...`), `deployedAt` (UTC), `action` (`release` or `rollback`), `buildUrl` |
| Written by | the delivery jobs `shelter-cd`, `shelter-cd-ui`, `shelter-cd-proxy` after the production release and its verification; the rollback jobs after a successful rollback |
| Read by | the rollback jobs (`TARGET_BUILD` = number of a delivery build, copied with `copyArtifacts`) and the automatic restore after a failed release or rollback (last successful build of the delivery or rollback job) |
| Code | `jenkins/render.groovy` (`recordRelease`, `releaseFromBuild`, `imageForRevision`), `jenkins/release-record.js` |

The delivery builds are named `#<number> <7-character commit>`, so the build list shows which version each one
delivered, and a rollback is started with the number of the build to go back to
(`shelter-rollback-proxy`, `TARGET_BUILD` = `7`).

## Rules and limits

- Only a build that completed a successful production release has a record, so only such a build can be chosen.
  A build that failed, was rolled back automatically or was never approved has none.
- If the record cannot be archived the release stays live and the build becomes UNSTABLE.
- A release delivered by tag (a manual run without `IMAGE_DIGEST`) is not recorded.
- The records live in the Jenkins history, so they are exactly as durable as the Jenkins home volume
  (`jenkins_home`). Until that volume is backed up outside the server, losing it loses the records.
- Build history is kept by `buildDiscarder`: delivery jobs 100 builds (records included), rollback jobs 30,
  CI 30 builds per branch (artifacts of the last 10). A release older than that cannot be chosen any more.
- The automatic restore after a failed release finds the previous digest in the last successful delivery or
  rollback build. If production is not what that build recorded (for example it was changed by hand), the
  image is taken by tag and the log says WARNING.
- A digest only works while the image exists on Docker Hub; do not delete images of releases you may restore.
- Permission to copy artifacts: the delivery and rollback jobs allow each other with `copyArtifactPermission`
  in their Jenkinsfiles. Job DSL rewrites the job configuration when the controller starts, so a job may lose
  that property until it has run once again. If a rollback fails with a message about the project or its
  permissions, run the delivery job once (or check the permission in the job's configuration).

## History

The records were first kept as JSON files on a git branch (`releases`), written with a deploy key. That was
replaced by the records in the Jenkins builds, which is the usual Jenkins practice and needs no extra secret;
the branch, its deploy key and its host key file were removed on 2026-11-14.
