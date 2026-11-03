# NBA Platform: Architecture Principles Review

*2026-10-10 · Review of the nine principles Altuğ proposed, assessed for an NBA decisioning platform. Companion to [research-and-architecture.md](research-and-architecture.md).*

Verdict key: **Adopt** = adopt as stated · **Adopt with guardrail** = adopt, with a stated limit · **Optional** = support it but don't require it

| # | Principle | Verdict | One-line reason |
|---|---|---|---|
| 1 | Composable microservices architecture | Adopt with guardrail | Strategies, channels and industry packs become swappable parts; keep the hot decision path composed in-process |
| 2 | Bounded context isolation | Adopt | NBA has naturally separate domains with different change rates and data shapes |
| 3 | API-only integration surface | Adopt with guardrail | Right for coupling control; widen to "contract-only" so events and bulk loads count |
| 4 | Event-driven backbone | Adopt | The learning loop, cache refresh and audit all depend on it |
| 5 | Stateless service design | Adopt with guardrail | Essential for scaling; allow rebuildable in-memory caches |
| 6 | Cloud-agnostic deployment | Adopt | Already the baseline; draw the portability line at open protocols |
| 7 | Horizontal pod scaling | Adopt | Decision traffic is spiky; pair with vertical sizing for model serving |
| 8 | Service mesh | Optional | Strong security/canary benefits, real latency and ops cost |
| 9 | Resilience quartet | Adopt | Mandatory for a real-time path; NBA needs a defined fallback answer |

---

## 1. Composable microservices architecture
**Merits for NBA.** NBA platforms evolve by adding *things*: a new channel, a new ranking strategy, a new industry's action types, a new model. Composability makes each of those a deployable or pluggable part rather than a core change. It also matches TM Forum's ODA "component" model, so a TMF-aligned buyer can slot our components into their landscape.

**Risk.** Over-decomposition on the decision path. A request that hops decision → eligibility → scoring → features → contact policy over the network adds 5 hops of latency and 5 failure points to a path that must answer in under 100 ms.

**Recommendation.** Composable at two levels:
- *Deployable components* for management, channel and learning capabilities (catalog, campaign, strategy, channel adapters, learning).
- *In-process plugins* (Java SPI) for decision-path stages: eligibility, scoring strategies and arbitration ship as versioned modules loaded by decision-service. Heavy models can still sit behind a separate model-serving service.

## 2. Bounded context isolation
**Merits.** The contexts have genuinely different language and lifecycles: *Catalog* (what can be offered), *Campaign* (when/to whom, approvals), *Decisioning* (real-time choice), *Party Profile & Consent*, *Contact Policy*, *Interaction & Outcome*, *Learning*, *Delivery*. Isolation lets the campaign team ship UI-heavy workflow changes weekly without touching the latency-critical decision engine.

**Guardrail.** No shared database, no shared domain library. The only shared code is *generated* contract code (from OpenAPI/AsyncAPI/Avro). Anti-corruption layers sit in front of customer systems of record (CRM, billing, core banking) so their models never leak in. This is also what keeps the platform industry-agnostic.

## 3. API-only integration surface
**Merits.** Every integration is versioned, documented, testable and governable; no hidden coupling via shared tables or file drops. It is what makes TMF conformance meaningful.

**Guardrail.** Pure request/response APIs are wrong for two NBA flows: high-volume event ingestion (clickstream, transactions) and bulk loads (nightly party/feature snapshots, outbound lists of millions). Recommend defining it as **"contract-only integration"**: REST (OpenAPI/TMF), events (AsyncAPI on Kafka) and bulk datasets (schema-registered files in object storage) are all first-class, versioned contracts. Anything not covered by a published contract is forbidden.

## 4. Event-driven backbone
**Merits.** NBA is a closed loop: decide → act → observe → learn. Events carry decisions, impressions, responses and outcomes to the learning, analytics and contact-policy contexts without coupling them to decision-service. Events also push config/model changes to the decision plane (no per-request lookups) and form the immutable audit log.

**Guardrails.**
- The decision request itself stays synchronous (TMF680); a caller in a web session needs an answer now.
- Use transactional outbox for every state change that emits an event.
- Use event-carried state transfer, not full event sourcing, except for the decision log, which is append-only by nature.
- Partition by tenant + party for per-party ordering; size partition counts early because they cap consumer parallelism.

## 5. Stateless service design
**Merits.** Any pod can serve any request, so scaling, rolling deploys and failover are trivial. Critical for the decision plane.

**Guardrail: "stateless, not cacheless".** Decision pods hold in-memory snapshots of rules, campaigns, model weights, bandit parameters and shadow prices. These are derived, rebuildable from events/storage, and identical across pods, so they don't break statelessness. True mutable state (contact counters, feature values, bandit updates) lives in external stores (Redis-compatible, Postgres) or in the learning service.

## 6. Cloud-agnostic deployment
**Merits.** Enterprise buyers run on different clouds and on-prem; regulated industries often require on-prem or sovereign cloud.

**Guardrail.** Draw the portability line at **open protocols, not products**: Kubernetes API, Kafka wire protocol, PostgreSQL wire protocol, Redis protocol, S3 API, OIDC, OpenTelemetry. Managed services that speak those (MSK, Cloud SQL, ElastiCache, Event Hubs Kafka) are allowed per deployment; proprietary APIs are not used in core code. Cost: you give up some managed conveniences (e.g. DynamoDB, Pub/Sub-specific features).

## 7. Horizontal pod scaling
**Merits.** Decision traffic is spiky (campaign launches, app peaks, billing days). Stateless pods behind HPA scale out cheaply.

**Guardrails.**
- Scale on the right signal: request rate/latency for decision-service, consumer lag (KEDA) for event consumers, not just CPU.
- Kafka partitions limit consumer scale-out; plan them up front.
- Some workloads scale better vertically: large model-serving pods (memory), optimizer solve jobs (CPU). Use VPA recommendations and dedicated node pools there.
- Cluster autoscaler or Karpenter for node capacity; PodDisruptionBudgets for safe drains.

## 8. Service mesh
**Merits.** mTLS and identity between services (zero trust) without app code; uniform telemetry; **traffic splitting**, which is very useful for NBA: canary a new decision-service build or route 10% of traffic to a new strategy image.

**Costs.** Sidecar proxies add roughly 1–3 ms per hop and CPU/memory per pod, plus a non-trivial ops skill set. Some customers' platforms already run a mesh, others forbid one.

**Recommendation.** Mesh-*ready*, not mesh-*dependent*: the platform must be secure and resilient without a mesh (app-level OIDC/mTLS options, Resilience4j), and ship tested configs for Istio ambient mode (no sidecars, lower latency) and Linkerd. Strategy A/B is done in-app anyway (it must be per-party sticky, which a mesh can't do).

## 9. Resilience quartet
Assumed meaning: **timeouts, retries, circuit breakers, bulkheads** (please confirm).

**Merits.** The decision path calls features, scoring and contact policy within a tight budget; one slow dependency must not take the whole channel down.

**NBA-specific guardrails.**
- **Deadline budget:** each request carries a total deadline (e.g. 80 ms); downstream timeouts derive from what's left.
- **Retries:** only on idempotent reads, with retry budgets and jitter; never retry at both mesh and app layer (retry storms).
- **Circuit breakers** per dependency, with a defined **fallback**: if scoring is down, rank by rules + static priority; if everything is down, return a configured safe default action set. A degraded answer is better than no answer for a call-center agent.
- **Bulkheads:** separate thread pools/connection pools per channel and per dependency so batch outbound runs can't starve real-time inbound.
- Add **rate limiting / load shedding** at the gateway as a fifth guard.

---

## Principles I suggest adding
1. **Contract-first**: OpenAPI/AsyncAPI written and reviewed before code; contract tests in CI.
2. **Observability by default**: OpenTelemetry traces, metrics and logs in every service; every decision traceable end to end.
3. **Explainable and auditable decisions**: reason codes, rule traces and model versions on every decision; immutable log.
4. **Privacy and security by design**: consent enforced in eligibility; PII minimization; zero-trust auth.
5. **Multi-tenancy from day one**: tenant id in every API, event key and data row.
6. **Idempotency everywhere**: idempotency keys on write APIs and consumers, since events are at-least-once.

---

## Decisions (agreed with Altuğ, 2026-10-10)

| ID | Decision |
|---|---|
| D1 | Decision path stages (eligibility, scoring, arbitration) are **in-process plugins** (Java SPI) inside decision-service; heavy models may sit behind a model-serving service. |
| D2 | "API-only" is defined as **contract-only integration**: REST (OpenAPI/TMF), Kafka events (AsyncAPI + schema registry) and bulk files (schema-registered, in S3-compatible storage) are all first-class contracts. |
| D3 | Service mesh is **optional and supported**: platform works without one; tested configs shipped for Istio ambient and Linkerd. |
| D4 | Resilience quartet = timeouts, retries, circuit breakers, bulkheads, plus a **configurable safe default answer** when scoring or dependencies fail, and gateway rate limiting. |
| D5 | Validation targets: **local kind**, **Amazon EKS** (first managed Kubernetes) and **Red Hat OpenShift** (restricted SCC, Routes, Operators: Strimzi/AMQ Streams, CloudNativePG/Crunchy). Charts must run non-root with arbitrary UIDs. |
| D6 | All six added principles adopted: contract-first, observability by default, explainable/auditable decisions, privacy & security by design, multi-tenancy from day one, idempotency everywhere. |
| D7 | **Monorepo** (Maven multi-module + per-service Helm charts, independent versioned images and pipelines per service). |
| D8 | Language: Java 21 / Spring Boot 3; Python for ML training only. |

Working sizing assumption until confirmed: 10M parties per tenant, 2,000 real-time decisions/sec peak, p99 < 100 ms, 5M-party outbound runs nightly.
