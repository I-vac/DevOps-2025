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
