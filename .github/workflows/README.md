# CI/CD Workflows

This README explains what the 7 pipelines under `.github/workflows/` do and why they're built this way. If you just want to understand how the DevSecOps setup works end to end, this one file should cover it — no need to read every YAML line by line.

## Overview

| File | Covers | Trigger | Does |
|---|---|---|---|
| `ci-java.yml` | Java backend | push/PR to main (path-filtered) + manual | Build, test, SCA/SAST, coverage, image build/scan, deploy to ECS |
| `ci-python-ml-agent.yml` | ML service | same | Same, Python stack |
| `ci-web.yml` | Web frontend | same | Same, Node stack, artifact goes to S3 |
| `ci-android.yml` | Android app | same | Same, Gradle stack, artifact goes to S3 |
| `secret-scan.yml` | whole repo | push/PR to main | Gitleaks secret scanning, fail-closed |
| `terraform-plan.yml` | `terraform/` | push/PR to main (path-filtered) + manual | `terraform plan` + Checkov IaC scan |
| `dast-scan.yml` | the three deployed services | daily schedule + manual | ZAP against the live environment, not tied to push/PR |

The four `ci-*.yml` files share the same skeleton, just with each ecosystem's own tooling:

```
checkout → SCA (dependency scan) → upload SCA report + SARIF → SAST (static analysis) → upload SAST report + SARIF
         → unit tests (with coverage) → upload coverage report → build artifact → image scan (Trivy, Java/ML only)
         → upload Trivy SARIF → deploy (push to main only)
```

## Core principles

### 1. Scans report; they don't gate the build or deploy

Every scan step across the four CI pipelines (`SCA`, `SAST`, `Trivy`) runs with `continue-on-error: true` — a high-severity finding doesn't turn the pipeline red or block a deploy. This is deliberate: with no dedicated person triaging the Security tab before every release yet, a hard gate would just get bypassed or ignored under release pressure. Better to let scanning run reliably and keep building up visibility in GitHub Code Scanning first, then decide later whether to tighten specific checks into real gates.

The one exception is `secret-scan.yml` (Gitleaks), which is fail-closed — a secret landing in git history isn't something you can "record and discuss later," so that one blocks on the spot.

### 2. SARIF results all land in Code Scanning, one category per tool

Every scan step converts its output to SARIF and uploads it via `github/codeql-action/upload-sarif`, each with its own `category:` (`dependency-check-java`, `spotbugs`, `bandit-ml`, `dependency-check-android`, `android-lint`, `eslint-web`, `trivy-image-java`, `trivy-image-ml`, `checkov`, `gitleaks`). This lets the Security tab be filtered per tool/module, and tracks each alert's open/fixed state over time instead of only living inside a run's artifact.

### 3. SARIF upload steps are guarded against missing files

Each SARIF upload step follows this pattern:
```yaml
if: always() && hashFiles('.../report.sarif') != ''
```
`always()` makes sure the upload still runs even if the scan step itself failed (not skipped). The `hashFiles(...)` check makes sure it only tries to upload when a report file actually exists, so a scan that produced nothing skips cleanly instead of erroring on a missing path. The steps after each scan (tests, builds) are also marked `if: always()`, so one scan step's outcome never cascades into skipping unrelated steps later in the same job.

### 4. Coverage is measured, not gated

All four modules run unit tests with coverage (JaCoCo/pytest-cov/Vitest coverage), and the report is uploaded as an artifact — but there's no minimum-coverage threshold blocking CI. The goal right now is an honest baseline across modules that started from very different places, not an artificial pass/fail line.

### 5. Deploys are decoupled from scanning, and only fire on push to main

Every deploy step (`Deploy to ECS`, `Upload to S3`) is conditioned on `github.event_name == 'push' && github.ref == 'refs/heads/main'`. PRs run tests and scans only; nothing from a PR branch ever reaches the live environment.

### 6. DAST is its own track — it scans a running service, not source code

`dast-scan.yml` isn't tied to push/PR because there's no live service to test yet at PR time. Instead it runs daily (UTC 18:00) plus on manual trigger, against the already-deployed dev environment: Java and ML are scanned with `action-api-scan` against their OpenAPI docs (covers each declared endpoint, better reach than a bare baseline scan), and the Web frontend — a static site with no OpenAPI — uses `action-baseline` with `-a` (ajax spider, so it can follow the SPA's client-side routes). All three scans are unauthenticated, so JWT-protected endpoints mostly come back as 401.

## Related outputs

- Full findings/fixes/re-scan record: `LoomyTrip-Security-Report.pdf`
- High-priority findings are tracked as Jira tickets in `ADproject`
