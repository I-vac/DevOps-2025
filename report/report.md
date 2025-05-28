# ITU‑MiniTwit – DevOps Monitoring & CI/CD Report

*MSc Group N* — Spring 2025 ⁄ jnol@itu.dk & ivni@itu.dk

---
# 1. System’s Perspective

## 1.1 Architecture Overview

The ITU-MiniTwit platform is **fully hosted on DigitalOcean** and embraces a containerised micro-service pattern managed via Docker Compose.  Two dedicated droplets implement a **classic split-brain topology**:

| Droplet         | Public IP       | Role                    | Main containers                                                                                             |
| --------------- | --------------- | ----------------------- | ----------------------------------------------------------------------------------------------------------- |
| **app-prod-01** | `161.35.71.145` | User-facing application | `nginx`, `minitwit-blue`, `minitwit-green`, `simulator-api`                                                 |
| **mon-prod-01** | `68.183.210.76` | Observability stack     | `prometheus`, `grafana`, `elasticsearch`, `kibana`, `filebeat`, `alertmanager`, `node-exporter`, `cadvisor` |

A **blue-green strategy** is enforced at the container layer: two identical backend containers run in parallel; a `/etc/nginx/conf.d/upstream.conf` symlink determines which revision receives live traffic.  State is isolated in a named Docker volume holding **SQLite** (WAL + shared-cache mode).  Structured JSON logs are mounted into `/var/lib/minitwit/logs`; `filebeat.yml` harvests and ships lines over the VPC to Elasticsearch.

### 1.1.1 Component Diagram (PlantUML)
<!-- image -->
> **Figure 1.** Logical architecture, deployment footprint and cross-droplet flows.

## 1.2 Technology & Tool Dependencies

| Layer         | Technology / Tool                                                | Purpose                                                                                                                    |
| ------------- | ---------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Cloud         | **DigitalOcean Droplets & VPC**                                  | Low‑friction IaaS, cheap static IPv4, private networking                                                                   |
| Runtime       | **Java 21 (Temurin)**                                            | Virtual threads (Project Loom), LTS support                                                                                |
| Build         | **Maven 3.9**                                                    | Deterministic builds, Surefire & SpotBugs plugins                                                                          |
| Pack/Run      | **Docker + Docker Compose**                                      | Environment parity, blue‑green pattern (services *minitwit‑blue*, *minitwit‑green*, *simulator‑api*)  |
| Data          | **SQLite**                                                       | Zero‑ops DB, WAL for write concurrency                                                                                     |
| Observability | **Prometheus**, **Grafana**, **JMX exporter**, **/metrics** HTTP | Metrics scrape & dashboards                                                                           |
| Logs          | **Filebeat**, **Elasticsearch**, **Kibana**                      | Structured log shipping & search                                                                      |
| Infra metrics | **node‑exporter**, **cAdvisor**                                  | Host & container resource usage                                                                                            |
| CI/CD         | **GitHub Actions**, **appleboy/ssh‑action**                      | Build, test, push, blue‑green deploy                                                                 |
| IaC           | **docker‑compose.yml (app & monitoring)**                        | Declarative stack definition                                                        |


## 1.3 Subsystem Interactions

### 1.3.1 End-user HTTP request
<!-- image -->

### 1.3.2 Simulator request path
<!-- image -->

## 1.4 Current System State & Quality Metrics
??


## 1.5 Rationale for Technology Choices (MSc)

1. **Java 21 LTS** – Virtual threads reduce thread-per-req overhead, ensure future-proof support.
2. **Docker/Compose** – Reproducible local dev & prod, blue-green implemented via container labels + NGINX.
3. **SQLite** – Meets workload (< 500 req/s), zero-maintenance, mitigated via WAL + pool.
4. **Prometheus/Grafana** – Open, query-flexible, no external latency.
5. **ELK** – Full-text debugging faster than Loki; Filebeat lightweight.
6. **DigitalOcean** – Simpler pricing vs. AWS, gives floating IPs & VPC out-of-box.
7. **GitHub Actions** – SaaS runners, secrets ephemeral, direct SSH deploy fits blue-green pattern.

---

# 2. Process Perspective

## 2.1 CI/CD Pipeline (GitHub Actions)

<!-- image -->

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

## 2.3 Monitoring & Alerting

* **Metrics** – Prometheus scrapes:

  * `/metrics` (application),
  * JMX exporter (JVM internals),
  * node‑exporter & cAdvisor (host / container).
* **Dashboards** – Grafana boards: *Service Latency*, *Simulator Throughput*, *Droplet Overview*.
* **Alerts** – Alertmanager routes *P95 latency > 300 ms 5 m* or *error‑rate > 2 %* to Slack; PagerDuty escalation for uptime < 99.5 %.

## 2.4 Logging & Aggregation

* **Log Format** – Logback JSON encoder (timestamp, level, traceId, userId, message).
* **Collection** – Filebeat side‑car tails `/var/lib/minitwit/logs/*.log`.
* **Indexing** – Elasticsearch ILM keeps 7 d hot, 21 d warm, 30 d delete; < 4 GB/day.
* **Visualisation** – Kibana saved searches: *Failed‑Login Attempts*, *Top 10 slow queries*.

## 2.5 Security Hardening
??

## 2.6 Scaling & Upgrades Strategy

??

# 3. Reflection Perspective

??

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
