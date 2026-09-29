# Documentation

The [root README](../README.md) is the summary. These pages hold the detail, one topic each.

**Product**

| Page | What it covers |
|---|---|
| [Overview](overview.md) | The problem, who it is for, the key concepts, how a store uses it, and the guarantees it gives |
| [Example: order fulfillment](example-order-fulfillment.md) | The demo store's process, step by step, with its participants and messages |

**Running it**

| Page | What it covers |
|---|---|
| [Getting started](getting-started.md) | The API, the web app, the Compose stack, the end-to-end tests, Postman and the operations endpoints |
| [Configuration](configuration.md) | Profiles and environment variables |

**How it works**

| Page | What it covers |
|---|---|
| [Domain model and rules](domain-model.md) | The resources, and the modeling, user and lifecycle rules |
| [Diagnosis, versions and AI review](diagnosis-and-versions.md) | What a diagram gets wrong, publishing into immutable versions, and the second opinion of a model |
| [Execution and simulation](execution.md) | Cases, tasks, conditions, messages, the store's clock, the simulated partners and the dashboard |
| [Security](security.md) | Roles and permissions, authentication, login protection, tenant isolation, sharing and passwords |
| [API reference](api-reference.md) | Every endpoint, pagination, concurrent edits, idempotency, auditing and errors |

**Engineering**

| Page | What it covers |
|---|---|
| [Architecture](architecture.md) | Stack, modules, request lifecycle, module boundaries, the version cache and the nightly purge |
| [Quality and testing](testing.md) | Test suites, coverage gates and the CI pipeline |
| [Design decisions](design-decisions.md) | Why each non-obvious choice was made |
| [Roadmap](roadmap.md) | What is done and what comes next |

The web app and the end-to-end suite have their own pages: [frontend/README.md](../frontend/README.md) and
[e2e/README.md](../e2e/README.md).
