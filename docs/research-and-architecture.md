# Next Best Action Platform: Research & Reference Architecture

*Version 0.1 · 2026-10-10 · Status: draft for review (Phase 1, documentation only)*

## 1. Summary

- **Recommended decisioning approach:** a layered *hybrid* pipeline. Hard rules and eligibility first, then propensity × value scoring, then contextual bandits for explore/exploit, then constrained arbitration (budgets, capacity, contact policy). Uplift models and reinforcement learning come later as pluggable strategies, not as the foundation.
- **Standards:** expose the platform through TM Forum Open APIs, mainly **TMF680 Recommendation**, **TMF681 Communication**, **TMF671 Promotion**, **TMF620 Product Catalog**, **TMF632/629 Party/Customer** and **TMF688 Event**. These APIs are telco-born but entity-generic, so the domain stays industry-neutral if we treat "product" as "offer/action" and "customer" as "party".
- **Architecture:** ~12 independently deployable services, split into a low-latency **decision plane** and an asynchronous **campaign/learning plane**, connected by a Kafka-compatible event backbone with schema-registered contracts.
- **Deployment:** Kubernetes + Helm/Kustomize, Kafka API (Apache Kafka, Redpanda, MSK, Confluent, Event Hubs), PostgreSQL, Redis-compatible cache, S3-compatible object storage, OpenTelemetry, Keycloak/OIDC. No managed-service lock-in in the core path.

---

## 2. What an NBA system has to decide

For a given **party** (customer, member, patient, citizen, account…) in a given **context** (channel, time, recent event, session intent), choose the best **action(s)** from a catalog (offer, message, service action, retention save, education nudge, *or do nothing*) such that:

1. the action is **allowed** (eligibility, consent, regulation, contact fatigue),
2. it is **relevant** (likelihood of positive response),
3. it is **valuable** (to the party and the business),
4. the portfolio respects **global constraints** (budget, inventory, agent capacity, channel volume, fairness),
5. the system **learns** from outcomes.

Each algorithm family below covers some of these well and others poorly, which is why the recommendation is a pipeline.

## 3. Decisioning algorithms: research and tradeoffs

| # | Approach | How it works | Strengths | Weaknesses | Role in this platform |
|---|---|---|---|---|---|
| A | **Business rules & eligibility** | Declarative predicates (DMN / CEL / Drools-style) filter the action catalog | Transparent, auditable, enforces regulation and consent; zero data needed | Does not rank; rule sprawl; no learning | **Mandatory stage 1** for every decision |
| B | **Propensity × value scoring** | Supervised models (GBM, logistic, DNN) predict P(response \| party, action, context); priority = P × value × weight (classic Pega/SAS "P·V·L·C" form) | Mature, explainable (SHAP), works offline-trained on history | Predicts *response*, not *causal effect*; feedback loops; needs history per action; cold start | **Default ranker** (stage 2) |
| C | **Uplift (causal) modeling** | Predicts incremental effect: P(Y\|treat) − P(Y\|control) using T-/S-/X-learners, causal forests, uplift trees | Targets *persuadables*, avoids wasting spend on sure-things and sleeping dogs; best ROI metric | Needs randomized control data; noisier; harder to validate; per-action models | **Phase 2 strategy** for retention/cost-heavy actions once holdout data exists |
| D | **Contextual bandits** | Online learners (LinUCB, Thompson sampling, ε-greedy over model scores) balance exploring new actions vs exploiting known good ones | Learns continuously; handles new actions and cold start; regret-bounded; cheap to serve | Optimizes immediate reward only; needs reliable, timely feedback; off-policy evaluation discipline required | **Stage 3 learning layer** wrapping B; also enables safe experiments |
| E | **Constrained optimization / arbitration** | Solve allocation across many parties: LP/ILP or Lagrangian dual pricing of constraints (budget, capacity, per-channel caps), applied in real time via shadow prices | Respects business-wide limits; global optimum rather than greedy per-party | Batch solve needed for exact answers; real-time uses approximations | **Stage 4 arbitration**; batch solver for outbound, dual prices for inbound |
| F | **Reinforcement learning** | Optimizes long-term value (CLV) over sequences of actions (DQN, offline RL, CQL) | Captures sequence effects and fatigue; optimizes lifetime outcomes | Data-hungry; offline evaluation hard; safety/explainability concerns; heavy ops | **Research track / Phase 3**, plugged in as a strategy behind the same interface |
| G | **LLM-assisted** | LLMs generate/personalize message content or reason over context | Content personalization, explanations for agents | Non-deterministic; cost/latency; governance | **Optional** content and explanation layer, never the arbiter |

### 3.1 Recommendation: layered decision pipeline

```
 Request (party, context, channel, slots=N)
   │
   ▼
 1. Candidate generation   – actions active for channel/time (catalog + campaign schedule)
 2. Eligibility & policy   – rules, consent, contact policy, suppression, fatigue caps   [A]
 3. Scoring                – propensity, value, (later) uplift per candidate          [B][C]
 4. Learning/exploration   – bandit adjusts scores, assigns control groups            [D]
 5. Arbitration            – priority = f(score, value, levers, shadow prices)        [E]
 6. Selection & diversity  – top-N with diversity/channel constraints
   │
   ▼
 Response (ranked actions + decision id + reason codes)  → Decision event logged
```

Why this shape:
- **Explainable and governable**: every decision has rule outcomes, model scores and reason codes written to an immutable decision log.
- **Strategy-pluggable**: stages 3–5 sit behind a `DecisionStrategy` SPI, so an uplift model, bandit or RL policy can be swapped per campaign/action group and A/B tested against each other.
- **Learns safely**: a persistent universal control group (e.g. 2–5%) plus bandit exploration gives unbiased data for uplift models and off-policy evaluation (IPS / doubly robust) before promoting new strategies.
- **Latency**: stages 1–6 must fit in **p99 < 50–100 ms** for inbound channels. Models are served as in-process ONNX or via a model-serving sidecar; features come from an online feature store.

### 3.2 Priority formula (starting point)

`priority = P(response) × Value × Lever × ContextWeight − Σ λ_k × cost_k`

where `Lever` is a business override, and `λ_k` are shadow prices from the arbitration solver for constraint *k* (budget, capacity). With uplift models, `P(response)` is replaced by `ΔP` (incremental response).

---

## 4. Standards-based APIs (TM Forum Open APIs)

TMF Open APIs are REST/JSON, follow the **TMF630 REST design guidelines**, and are published as OpenAPI specs with conformance test kits. Their resources (Party, ProductOffering, Recommendation, Event…) are generic enough to serve any industry if we keep vocabulary neutral.

| Platform capability | TMF Open API | Use |
|---|---|---|
| Get next best actions | **TMF680 Recommendation Management** | Primary decision API: `POST /recommendation` with party, channel, context; returns ranked `recommendationItem`s |
| Action / offer catalog | **TMF620 Product Catalog Management** | Actions modeled as `ProductOffering`s (or generic "offering"); categories, validity, pricing for value |
| Campaigns & promotions | **TMF671 Promotion Management** | Campaign/promotion definitions, eligibility criteria, validity periods |
| Parties | **TMF632 Party Management**, **TMF629 Customer Management**, **TMF669 Party Role** | Who the decision is for; roles (customer, prospect, agent) |
| Consent & privacy | **TMF644 Privacy Management** | Consent, opt-outs, purpose limitation feeding eligibility |
| Outbound delivery | **TMF681 Communication Management** | Send messages (email/SMS/push/in-app) through channel adapters |
| Interaction capture | **TMF683 Party Interaction Management** | Log impressions, accepts, declines from any channel |
| Holdings context | **TMF637 Product Inventory** (optional) | What the party already has (avoid offering owned products) |
| Event subscriptions | **TMF688 Event Management** + standard TMF `/hub` listener pattern | External systems subscribe to decision/response events |
| Access control | **TMF672 User Roles & Permissions** (optional) | Campaign designer/approver roles |
| Orchestration | **TMF701 Process Flow** (optional) | Long-running multi-step journeys |

**Gaps** (no direct TMF API; expose as OpenAPI 3.1 in the same TMF630 style, under `/nba/v1`): decision strategy/arbitration configuration, experiments & control groups, model registry metadata, contact policy, simulation. Use TMF-style `@type`/`@baseType`/`@schemaLocation` polymorphism so they extend cleanly.

### 4.1 Keeping it industry-agnostic

- **Neutral core vocabulary:** Party, Action (Offering), Campaign, Channel, Interaction, Outcome, Decision, Constraint. No "subscriber", "SIM", "policyholder" in core schemas.
- **Extension by polymorphism:** industry specifics live in `@type` extensions and characteristic maps (`characteristic: [{name, value}]`, as TMF does), validated by pluggable JSON Schemas per tenant.
- **Configurable feature definitions:** features are declared in the feature store, not hard-coded.
- **Industry packs:** optional bundles (sample actions, rules, features, channel adapters) for telco, banking, retail, insurance, kept outside the core repo.
- **Multi-tenancy:** tenant id on every resource, event and topic key; per-tenant schemas and models.

---

## 5. Microservice decomposition

Bounded contexts, each owning its data, independently deployable, communicating via APIs (sync) and events (async).

### 5.1 Decision plane (real-time, latency-critical)

| Service | Responsibility | Store | API |
|---|---|---|---|
| **decision-service** | Runs the pipeline; stateless; horizontally scaled | none (reads caches) | TMF680 |
| **eligibility-engine** (library or service) | Evaluate rules/policies (CEL or DMN) compiled from config | in-memory snapshot | internal gRPC |
| **scoring-service** | Serve propensity/uplift models (ONNX Runtime, or KServe/Seldon/BentoML) | model artifacts in object storage | internal gRPC |
| **feature-service** | Online features per party (recent events, aggregates) | Redis-compatible / Cassandra-ScyllaDB | internal gRPC |
| **contact-policy-service** | Fatigue caps, suppression, quiet hours, per-channel limits | Redis + Postgres | internal |

### 5.2 Campaign & management plane

| Service | Responsibility | Store | API |
|---|---|---|---|
| **action-catalog-service** | Actions/offers, value, validity | PostgreSQL | TMF620 |
| **campaign-service** | Campaigns, schedules, targeting, approval workflow, versioning | PostgreSQL | TMF671 + `/nba/v1/campaign` |
| **strategy-service** | Arbitration formulas, levers, strategy-to-campaign binding, experiments | PostgreSQL | `/nba/v1/strategy`, `/experiment` |
| **party-profile-service** | Party facade/cache over systems of record, consent | PostgreSQL / cache | TMF632/629, TMF644 |
| **outbound-orchestrator** | Batch/triggered outbound runs, calls decision-service at scale, applies batch arbitration | PostgreSQL | `/nba/v1/outbound-run` |
| **channel-gateway** | Adapters to email/SMS/push/CRM/contact center | outbox table | TMF681 |
| **interaction-service** | Capture impressions/responses from all channels | Kafka → lakehouse | TMF683 |

### 5.3 Learning plane

| Service | Responsibility |
|---|---|
| **learning-service** | Consumes decision + outcome events, joins them (attribution windows), updates bandit parameters online, emits training sets |
| **model-registry** | MLflow-compatible registry; model promotion gated by off-policy evaluation |
| **optimizer-service** | Periodic LP/ILP solves (OR-Tools / HiGHS) publishing shadow prices to decision plane |
| **analytics/reporting** | Campaign KPIs, incremental lift vs control, dashboards |

Cross-cutting: **API gateway** (Kong / Envoy Gateway / APISIX), **identity** (Keycloak or any OIDC provider), **admin UI** (React SPA, backend-for-frontend).

### 5.4 Language

Default **Java 21 + Spring Boot 3** for business services (mature TMF reference implementations, strong ecosystem); **Python** for learning/training jobs and optionally model serving. Decision-service can be Java (GraalVM native or virtual threads) or Go if latency budgets demand it. Confirmed by the user on 2026-10-10.

---

## 6. Data & event architecture

### 6.1 Event backbone

- **Kafka API** as the contract (works with Apache Kafka, Redpanda, Strimzi on k8s, MSK, Confluent Cloud, Azure Event Hubs Kafka endpoint).
- **Schemas** in Avro or Protobuf with a registry (Apicurio or Confluent-compatible); **CloudEvents** envelope for interoperability; TMF688 hub for external subscribers.
- **Transactional outbox** (Debezium CDC or library) on every service that changes state and emits events, so DB writes and events never diverge.

Key topics (partitioned by `tenantId:partyId` for ordering per party):

| Topic | Producer | Consumers |
|---|---|---|
| `party.events` (behavioral/business events from source systems) | ingestion | feature-service, trigger evaluation |
| `nba.decision.made` | decision-service | learning, analytics, contact-policy |
| `nba.interaction.recorded` (impression/accept/decline) | interaction-service | learning, contact-policy, analytics |
| `nba.outcome.observed` (conversion, revenue) | ingestion | learning, analytics |
| `campaign.changed`, `catalog.changed`, `strategy.changed` | management services | decision plane cache refresh |
| `model.promoted`, `shadowprice.updated` | registry, optimizer | scoring-service, decision-service |

### 6.2 Storage

- **Operational:** PostgreSQL per service (database-per-service; CloudNativePG on k8s, or RDS/Cloud SQL/Azure DB).
- **Online features & counters:** Redis-compatible (Redis, Valkey, Dragonfly) or ScyllaDB at large scale.
- **Analytical/lakehouse:** Apache Iceberg tables on S3-compatible storage (MinIO / S3 / GCS / ADLS), queried with Trino or Spark; Feast as feature store bridging offline/online.
- **Decision log:** immutable, append-only (Kafka → Iceberg) for audit, explainability and off-policy evaluation.

### 6.3 Real-time triggers

Stream processor (Kafka Streams or Apache Flink) evaluates event patterns (e.g. "3 failed payments in 7 days") and calls decision-service for event-triggered NBA.

---

## 7. Scalability & resilience

- **Horizontal:** stateless decision/scoring services behind HPA (CPU + custom metrics such as request rate or consumer lag via KEDA). Kafka partitions bound consumer parallelism; size for peak.
- **Vertical:** VPA recommendations for model-serving pods (memory-heavy models); node pools with larger instances for optimizer jobs.
- **Latency:** decision plane reads only in-memory snapshots + one feature fetch; config changes pushed via events, not pulled per request.
- **Resilience:** timeouts, circuit breakers (Resilience4j), bulkheads; graceful degradation to a rules-only default ranking if scoring is unavailable; idempotent consumers; DLQs.
- **Multi-region:** active-active decision plane per region; Kafka MirrorMaker 2 / cluster linking for events.

## 8. Cloud-agnostic deployment stack

| Concern | Choice (portable) |
|---|---|
| Runtime | Kubernetes (EKS, AKS, GKE, OpenShift, on-prem) |
| Packaging | Helm charts per service + umbrella chart; Kustomize overlays per environment |
| GitOps | Argo CD or Flux |
| IaC | Terraform/OpenTofu modules per cloud, behind a common interface |
| Eventing | Strimzi Kafka or managed Kafka-API service |
| Databases | CloudNativePG or managed Postgres |
| Secrets | External Secrets Operator → Vault / cloud secret managers |
| Service mesh (optional) | Istio or Linkerd for mTLS, retries, traffic splitting (canary of strategies) |
| Observability | OpenTelemetry → Prometheus, Grafana, Tempo/Jaeger, Loki |
| CI/CD | GitHub Actions: build, test, contract tests, container scan, SBOM, push to OCI registry |
| API contracts | OpenAPI 3.1 (TMF specs + extensions), AsyncAPI for events; contract tests (Pact or Spring Cloud Contract); TMF CTK conformance runs |
| Security | OAuth2/OIDC, per-tenant scopes, mTLS in-cluster, PII encryption, audit log |

## 9. Governance & compliance

- Decision explainability: reason codes and rule traces per decision; SHAP summaries per model version.
- Model risk management: model cards, bias/fairness checks on protected attributes, human approval for promotion.
- Privacy: consent enforced in eligibility stage; data minimization in features; GDPR/KVKK right-to-erasure propagated by event.
- Simulation: replay historical traffic through a candidate strategy before go-live.

## 10. Suggested roadmap

| Phase | Scope |
|---|---|
| **0 (now)** | This document; confirm language, repo, MVP scope |
| **1: MVP** | Action catalog (TMF620), campaign (TMF671), decision-service (TMF680) with rules + propensity×value arbitration, interaction capture (TMF683), Kafka events, Helm charts, local stack via `kind` + docker compose |
| **2: Learning** | Contextual bandits (Thompson sampling), universal control group, learning-service, feature store, outbound orchestrator, channel gateway (TMF681) |
| **3: Optimization** | Uplift models, constrained optimizer with shadow prices, simulation, admin UI maturity |
| **4: Advanced** | Offline RL for sequence/CLV optimization, multi-region, industry packs |

## 11. Decisions so far (2026-10-10)

1. Language: **Java 21 / Spring Boot 3** for services (Python only for ML training jobs).
2. Repository: a **new GitHub repo**, created later once architecture principles are agreed.
3. Milestone: **research and architecture discussion first**, no code yet.

Still open: monorepo vs repo-per-service, and target scale (parties, decisions/sec, channels).
