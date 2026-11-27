# End-to-end tests

A real browser (Playwright, Chromium) goes through the whole application on **staging**, through the staging proxy
(`https://shelter-proxy-staging.onrender.com`), the way a user does. The delivery pipelines (`Jenkinsfile.cd`,
`.cd.ui`, `.cd.proxy`) run them in the stage *Staging: end-to-end tests*, after the smoke tests and before the
approval, so a release that breaks a user flow stops before production.

## What is tested

| Test | What it does | Writes data? |
|---|---|---|
| `tests/browse.spec.ts` | a visitor opens the home page, the list of dogs and, if there is a dog, its page | no |
| `tests/account.spec.ts` | a new user registers, signs out and signs in again | one user per run |
| `tests/admin-dogs.spec.ts` | the administrator adds a dog, a visitor who is not signed in opens its page, the administrator deletes it | a dog, deleted at the end (also when the test fails) |
| `tests/visits.spec.ts` | a new user books a visit to the first dog at a time of its own (one to twelve months ahead), finds it among their visits and cancels it | one user per run; the visit is cancelled at the end (also when a check fails after the booking) |

The tests are few on purpose: the unit, integration and acceptance tests of the API and the UI cover the details,
and a browser test is slow and can fail for reasons that are not a defect. These cover the flows whose failure a user
notices first.

## How it runs

The tests come from the same commit as the image being delivered (`jenkins/RELEASES.md`, *Revision of the tests*),
so a change of this folder is a new release candidate of all three components: CI builds their images for that
commit and each delivery runs these tests with them.

The pipeline builds the image from this folder (`Dockerfile`, based on the official Playwright image, which
has the browsers) and starts it with `docker run`. The report (HTML, with traces and screenshots of failed
tests) is copied out and kept as the artifact `e2e-report/` of the delivery build. Nothing is installed on the agent
or on the server. To run it by hand against staging, in this folder:

```
docker build -t shelter-e2e .
docker run --rm --init --ipc=host -e E2E_BASE_URL=https://shelter-proxy-staging.onrender.com shelter-e2e
```

## Limits

- `browse.spec.ts` opens a dog only if the staging schema has one; the administrator test, which makes its own dog,
  is the one that always checks a dog page.
- Every run adds one user to the staging schema; the API cannot delete users.
- The administrator test needs the account of the administrator of **staging** (Jenkins credential `staging-e2e-admin`,
  from the Docker secrets `staging_e2e_admin_email` and `staging_e2e_admin_password`). It must be an account with its
  own password, not the one of the production administrator. Without the credential the stage fails; it is never skipped.
- Sponsorships (a user applies, the administrator approves) and photo upload are not tested.
- One repeat of a failed test is allowed (`retries: 1`); a test that passes only the second time is reported as flaky.
- The version of `@playwright/test` (`package.json`) and the tag of the image (`Dockerfile`) must be the same;
  Dependabot is not set up for this folder because it would update one without the other. Update both together.
- The tests run as root in the container. They go only to our own staging address, which the Playwright
  documentation counts as trusted use.
