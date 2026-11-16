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

The tests are few on purpose: the unit, integration and acceptance tests of the API and the UI cover the details,
and a browser test is slow and can fail for reasons that are not a defect. These cover the flows whose failure a user
notices first.

## How it runs

The pipeline builds the image from this folder (`Dockerfile`, based on the official Playwright image, which
has the browsers) and starts it with `docker run`. The report (HTML, with traces and screenshots of failed
tests) is copied out and kept as the artifact `e2e-report/` of the delivery build. Nothing is installed on the agent
or on the server. To run it by hand against staging, in this folder:

```
docker build -t shelter-e2e .
docker run --rm --init --ipc=host -e E2E_BASE_URL=https://shelter-proxy-staging.onrender.com shelter-e2e
```

## Limits

- The staging schema can be empty, so the dog is opened only if there is one. A later test that creates its own dog
  (as the admin) will make this unconditional.
- Every run adds one user to the staging schema; the API cannot delete users.
- The tests that need the admin account (creating a dog, approving a sponsorship) are not written yet.
- One repeat of a failed test is allowed (`retries: 1`); a test that passes only the second time is reported as flaky.
- The version of `@playwright/test` (`package.json`) and the tag of the image (`Dockerfile`) must be the same;
  Dependabot is not set up for this folder because it would update one without the other. Update both together.
- The tests run as root in the container. They go only to our own staging address, which the Playwright
  documentation counts as trusted use.
