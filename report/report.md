# ITU‑MiniTwit – DevOps Monitoring & CI/CD Report

*MSc Group N* — Spring 2025 ⁄ jnol@itu.dk & ivni@itu.dk

> **Note:** All diagrams are rendered from embedded **PlantUML** sources by the Pandoc + `--filter pandoc-plantuml` step in the CI job.

---

## 1  Introduction

This report documents the design, implementation, operation, and continuous evolution of our **ITU‑MiniTwit** system.

```
/DevOps‑2025
 ├─ report/
 │   ├─ report.md          ← this file
 │   └─ build/*.pdf        ← CI artefact
 ├─ src/                   ← Java source (MiniTwit)
 ├─ docker‑compose.yml     ← App stack
 ├─ monitoring/            ← Observability stack
 └─ .github/workflows/     ← CI/CD
```

---

## 2 System Perspective

## 3 Process Perspective

### ✅ CI/CD Pipeline Overview

**Tools Used:**

* **GitHub Actions**: CI/CD orchestration
* **Docker Compose**: Service definitions and environment management
* **Maven**: Java build and testing
* **Python (requests)**: API endpoint tests
* **SSH Deploy (appleboy/ssh-action)**: Remote deployment to Droplet

**Stages:**

1. **`test-java`** – Java Unit Testing:

   * Build the simulator backend using Maven
   * Runs unit tests with `mvn clean test`

2. **`lint`** – Config Validation:

   * Validates `docker-compose.yml` and monitoring configs

3. **`build-and-test`** – Integration Testing:

   * Builds `minitwit` and `simulator-api`
   * Runs in isolated throwaway containers using test-only volumes
   * Waits for the health endpoint (`/health`)
   * Runs **functional API tests** (register/login/post timeline)

4. **`deploy`** – Blue-Green Deployment:

   * Pulls new code on the Droplet
   * Builds and deploys to the *inactive* version (blue or green)
   * Runs health checks on the new container
   * Swaps NGINX config symlink
   * Gracefully stops and removes the previous version

---

### 📊 Monitoring Strategy

**Tools Used:**

* **Prometheus**: Metrics scraping
* **Grafana** (implied for dashboards)
* **cAdvisor**: Container-level CPU, memory, I/O metrics
* **Node Exporter**: OS-level system metrics
* **Custom App Metrics**:

  * HTTP latency (per route)
  * DB query latency

**Prometheus Targets:**

* `app-http`: HTTP metrics from `minitwit` and `simulator-api`
* `app-jmx`: JVM metrics (on separate ports)
* `cadvisor` and `node-exporter`: Docker and system stats
* All targets use HTTPS with `insecure_skip_verify` (certs used but not CA-signed)

---

### 📋 Logging and Aggregation

**Logging Strategy:**

* All services log to `/data/logs/*.log` (volume-mounted)
* Logs are in **multiline timestamped format** (`^\d{4}-\d{2}-\d{2}`) for correct aggregation

**Aggregation:**

* **Filebeat** collects logs from all services via shared volume `minitwit-logs`
* Filebeat forwards logs to **Elasticsearch**
* **Kibana** used for exploration and visualization

---

### 🔒 Security Assessment Summary

**Findings:**

* Session-based login with bcrypt password hashing ✅
* Input validation on forms ✅
* SQL injection protection via prepared statements ✅
* Partial XSS protection (Freemarker templates escape by default) ⚠️
* No CSRF or rate limiting implemented ❌

**Hardening Steps Taken:**

* **BCrypt** used for secure password storage
* Session timeout set (300s)
* Input validation during registration/login
* HTTPS enforced via NGINX + Certbot
* NGINX denies access to direct port 80 (auto-redirects to HTTPS)
* Docker containers run with restart policies and logs are centralized

**Future Hardening Suggestions:**

* Add CSRF tokens on forms
* Rate-limit failed login attempts
* Explicitly escape all template variables

---

### 📦 Scaling and Upgrade Strategy

**Strategy:**

* **Blue-Green Deployment** implemented:

  * Two identical service definitions (`minitwit-blue`, `minitwit-green`)
  * NGINX switches between them via symlinked config
  * Ensures zero downtime
  * Rollbacks are instant by swapping symlink back

**Scaling:**

* Shared Docker volumes used for data and logs
* Metrics and logs are centralized and decoupled from app containers
* With containerization, horizontal scaling is trivial (can spin up more app containers behind a load balancer)

**Upgrade Notes:**

* Every deployment builds a fresh image from Dockerfile
* Health checks (`/health`) used to verify readiness before switching traffic
* Deprecated containers are cleaned up post-deployment


## 4 Reflection Perspective

---

### **GitHub Actions** as Our CI/CD Platform

We performed a structured literature- and feature-based comparison before committing to **GitHub Actions**.  The criteria below are the same ones we use throughout the project (cost, maintenance effort, ecosystem fit, security, and learning value).

| Criterion                                      | GitHub Actions                                                                                                                        | Why it meets our needs                                                                                                             |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| **Native integration with our code host**      | Lives inside every GitHub repo; PR checks and annotations appear exactly where we review code.                                        | Keeps feedback loop short—no context-switching or webhook glue.                                                                    |
| **Cost for a student project**                 | Free unlimited minutes for public repos and generous private-repo allowance under the GitHub Student Pack.                            | Zero budget impact; no “rate-limit anxiety” during heavy iteration weeks.                                                          |
| **Maintenance overhead**                       | Fully managed runners; updates, patching and autoscaling are handled by GitHub.                                                       | Lets us focus on pipeline logic (tests, scans, deploy) rather than administering a Jenkins/GitLab server.                          |
| **Action marketplace**                         | 20 000 + reusable actions (e.g. `setup-java`, `trivy-action`, `appleboy/ssh-action`).                                                 | We composed the entire pipeline without writing bespoke Bash wrappers, accelerating delivery and reducing error-surface.           |
| **Secrets management & supply-chain security** | Encrypted repository and environment secrets; built-in OIDC tokens for cloud deploy.                                                  | Aligns with the security controls we list in § 2.4—no plaintext secrets, audit trail for every secret read.                        |
| **Container & service support**                | Jobs run in Docker-enabled Ubuntu images; `services:` stanza spins up multi-container integration-test stacks that mirror production. | Exactly matches our need to boot the monitoring stack (`docker compose … up -d`) during pipeline runs.                             |
| **Learning curve vs. course time-box**         | Declarative YAML, mirrors examples shown in the lectures.                                                                             | Fast on-boarding for all group members; aligns with the course’s emphasis on *practical DevOps*, not pipeline framework internals. |

#### Alignment with MSc Learning Objectives

* **DevOps principles & CI/CD practice** – tight PR-gated checks, automatic deploys and security scans demonstrate continuous integration and delivery in a single, visible toolchain.
* **Infrastructure-as-Code** – the pipeline itself is version-controlled YAML; every edit is peer-reviewed like application code, fulfilling the “document and explain all steps” outcome.
* **Security & maintenance** – managed runners eliminate the need to harden a self-hosted CI server, while secrets-scoping and Trivy/Dependabot actions embed security controls directly into the delivery flow.
* **Scalability** – GitHub auto-scales runners, meaning our throughput grows automatically when we parallelise matrix jobs (e.g. Java 21 + Python 3.x).
* **Reflection & continuous improvement** – metrics such as *time-to-green* and *failed-build rate* are available in the Insights tab, giving us quantitative feedback on process health.

In summary, **GitHub Actions** offers the **lowest operational friction** and **highest pedagogical value** for a GitHub-hosted university project: no extra infrastructure to manage, a rich marketplace of audited building blocks, first-class security features, and an experience that keeps every DevOps feedback signal inside the same developer workflow.
