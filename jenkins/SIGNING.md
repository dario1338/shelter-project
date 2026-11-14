# Signing of the images

The digest of an image (`sha256:...`) says **what** the content is. A signature says **who** pushed it:
only the CI that holds the signing key can produce a signature that verifies against the public key in this
repository. Delivery and rollback refuse an image without such a signature.

## How it works

| Step | Where | What happens |
|---|---|---|
| Sign | `Jenkinsfile.ci`, function `signImage`, right after `docker push` of each component | `cosign sign` over `name@sha256:<digest>`; the signature is stored in Docker Hub next to the image |
| Verify before delivery | `Jenkinsfile.cd*`, stage *Validate parameters* | `render.verifySignature(env.IMAGE_URL)`; an unsigned or wrongly signed image stops the release before staging |
| Verify before rollback | `Jenkinsfile.rollback.ui`, `.rollback.proxy`, `.rollback` (with `TARGET_BUILD`) | the same check on the digest of the target release, before the approval is asked |

Signing happens only in the `main` branch stages that push images, after the tests, the Quality Gate and the
Trivy scan have passed; pull request builds do not push and do not sign.

The key pair is made once with `cosign generate-key-pair`. The public key is `jenkins/cosign.pub` (in Git).
The private key (encrypted) and its password are Docker secrets of the controller (`cosign_key`,
`cosign_password`), exposed to the pipelines as the credentials `cosign-key` and `cosign-password`, and used
only by `signImage`.

To check an image by hand (needs cosign): `cosign verify --key jenkins/cosign.pub --insecure-ignore-tlog=true docker.io/dario1338/shelter-proxy@sha256:<digest>`

## Choices: standard or our own design

| Choice | Standard? | Alternative |
|---|---|---|
| Sign by digest, signature stored in the registry | Standard (cosign's normal mode) | Signing a tag (rejected: a tag can move) |
| Our own key pair, private key as a CI credential | Supported by cosign and common for CI systems without an identity provider | Keyless (OIDC): recommended where the CI issues OIDC tokens (GitHub Actions, GitLab CI); on Jenkins it needs an own OIDC provider and an own Sigstore. Key in a KMS (AWS, Google, Azure, Vault): the key cannot be stolen as a file, needs an account and about 1 USD per month at AWS |
| No transparency log: signing config `jenkins/cosign-signing-config.json` without services, verify with `--insecure-ignore-tlog` | The signing config is the way cosign 3 documents for signing without the public log (its message for the old flag says so). The public log is the default and is meant for keyless | Upload to the public Rekor log: it would publish the image name and digest, and gives an audit trail we do not use |
| Verification in the delivery pipeline | Standard on Jenkins and other CI | Verification at run time (admission control, e.g. Kyverno) exists for Kubernetes; Render has no such hook |
| cosign pinned to a release and its SHA-256 | Same practice as for Trivy and Node in `jenkins/agent/Dockerfile` | - |

The signing config is a 106-byte file generated with `cosign signing-config create --out ...` and no services. Cosign is
pinned to a release because this part of its interface changed between major versions: an update is a reviewed
change, tested with a real signature.

## Limits

- The signature proves that the holder of the key signed the image, not that the code is correct. Whoever
  controls Jenkins (or can change a Jenkinsfile that runs on `main`) can sign anything. The key is a
  GLOBAL credential; with more people it belongs in a Jenkins folder with restricted access.
- Releases from before signing was introduced have no signature. They cannot be the target of a rollback
  through Jenkins (the rollback stops before the approval, with the cosign error). The automatic rollback
  after a failed release restores the version that was running and does not verify it.
- Manual delivery without a digest (tag only) is not verified; the log says so.
- Signatures are objects in Docker Hub (tags `sha256-<digest>.sig`). Deleting them, or the image, makes
  rollback to that release impossible; Docker Hub retention must not remove them.
- The private key is a file in `/opt/shelter-jenkins/secrets` and inside `jenkins_home`; it is protected by
  its password and by access to the server, and must be part of the backup of the secrets.

## Replacing the key

Generate a new pair, replace `cosign_key` and `cosign_password`, rebuild and restart the controller
(`build jenkins`, `up -d jenkins`), replace `jenkins/cosign.pub` in a pull request. Images signed with the
old key do not verify against the new one; if rollbacks to them must stay possible, keep the old public key
next to the new one and verify against both (not implemented).
