# NBA Platform

An industry-agnostic **Next Best Action** campaign management platform: independently deployable Java 21 /
Spring Boot microservices behind TM Forum Open API contracts, connected by a Kafka-API event backbone, deployable
to any Kubernetes (kind, EKS, OpenShift).

The design rationale is in [docs/research-and-architecture.md](docs/research-and-architecture.md) and the agreed
principles and decisions are in [docs/architecture-principles.md](docs/architecture-principles.md).

## How a decision is made

```mermaid
flowchart LR
  subgraph mgmt[Management plane]
    CAT[action-catalog-service<br/>TMF620]
    CMP[campaign-service<br/>TMF671]
  end
  subgraph rt[Decision plane]
    DEC[decision-service<br/>TMF680]
  end
  subgraph fb[Feedback]
    INT[interaction-service<br/>TMF683]
  end
  CAT -- nba.catalog.offering.changed --> K[(Kafka API)]
  CMP -- nba.campaign.changed --> K
  INT -- nba.interaction.recorded --> K
  K -- snapshot + learning --> DEC
  DEC -- nba.decision.made --> K
  CH[Channels: web, app, contact center] -- POST recommendation --> DEC
  CH -- POST partyInteraction --> INT
```

The decision service runs a pipeline of **plugins** (in-process, swappable, each switchable by property):

| Stage | Core plugin | What it does |
|---|---|---|
| Candidates | `campaign-candidates` | Actions of every active campaign valid now on the request channel |
| Eligibility | `consent`, `eligibility-rules`, `contact-policy`, `control-group` | Opt-out and channel consent, [campaign rules](docs/eligibility-rules.md), daily impression caps, stable hold-out groups for lift measurement |
| Scoring | `propensity-scoring` with scorers `logistic`, `remote` | Each offering's propensity model, routed to a scorer plugin: in-process logistic regression, or a Python-served model on a model server ([scoring](docs/scoring.md)) |
| Learning | `thompson-sampling` | Contextual Thompson sampling: Beta posterior per campaign x offering, prior centred on the model score |
| Arbitration | `priority-pvl` | priority = propensity x value x business lever |
| Selection | `top-n` | Best campaign per offering, top N |

The pipeline runs under a deadline (80 ms default), a per-channel bulkhead and a circuit breaker. Failures degrade
in two levels: a model that fails, times out or returns nonsense is replaced by the default prior **for that action
only** (`SCORING_FALLBACK`), and rules, consent and contact policy still apply. If the whole pipeline fails, times
out or the breaker is open, the API answers with the tenant's configured **safe default** actions, `fallback: true`
and a `fallbackReason`; safe defaults still honour opt-out and channel consent. Every decision, including each
suppressed candidate and its reason code and each item's explanation (model, top feature contributions,
propensity x value x lever), is written to the `nba.decision.made` log. `?explain=true` returns the same
explanation on the API.

Adding a strategy (uplift model, RL policy) means shipping a jar with a `DecisionStage` bean; a new kind of
propensity model is a `PropensityScorer` bean. See `services/decision-service/decision-spi`.

## Repository layout

| Path | Contents |
|---|---|
| `contracts/openapi` | OpenAPI 3.1 contracts for each TMF API subset |
| `contracts/asyncapi` | AsyncAPI 3.0 contract for all events (CloudEvents envelope) |
| `libs/event-contracts` | Java bindings for the event contracts |
| `libs/platform-starter` | Cross-cutting only: tenancy, TMF630 errors, JSON Merge Patch, transactional outbox, observability |
| `services/action-catalog-service` | TMF620 ProductOffering (actions) |
| `services/campaign-service` | TMF671 Promotion (campaigns) |
| `services/decision-service` | `decision-spi`, `decision-plugins-core`, `decision-app` (TMF680 Recommendation) |
| `services/interaction-service` | TMF683 PartyInteraction (impressions and responses, idempotent) |
| `ui/console` | Web console (React + TypeScript) for managing actions and campaigns and trying decisions |
| `deploy` | Docker Compose, Helm charts with kind/EKS/OpenShift overlays, Kafka topics |

## Run it locally

Requires Docker.

```bash
docker compose -f deploy/compose/docker-compose.yml up --build
```

Open the console at http://localhost:3000, click **Load demo data** on the Overview page, then go to the
**Decision playground**. Change the customer attributes (for example `tenureMonths`, `optOut = true`,
`consent.web = false`) and run a decision to see the ranked actions, the scores behind them, and which
candidates were dropped and why. Record Shown, Accepted or Declined on a result and it appears under
**Interactions**. The tenant switch in the sidebar keeps test data apart.

If port 3000 (or any other) is already taken, pick another one:
`CONSOLE_PORT=8090 docker compose -f deploy/compose/docker-compose.yml up --build`, then open
http://localhost:8090. `CATALOG_PORT`, `CAMPAIGN_PORT`, `DECISION_PORT`, `INTERACTION_PORT`, `POSTGRES_PORT` and
`KAFKA_PORT` work the same way; the list is at the top of the compose file.

### Console development

```bash
cd ui/console
npm ci
npm run dev        # http://localhost:5173, proxies the TMF APIs to the services on 8081-8084
npm run lint && npm run typecheck && npm test
npm run e2e        # browser tests against a mocked API (Playwright)
```

Set `CATALOG_URL`, `CAMPAIGN_URL`, `DECISION_URL` or `INTERACTION_URL` to point the dev proxy elsewhere.
In containers the console is served by nginx, which proxies the same paths to the services.

### Without the console

Create an action and a campaign, ask for a decision and report the response:

```bash
T='X-Tenant-Id: demo'; J='Content-Type: application/json'

OFFER=$(curl -s localhost:8081/tmf-api/productCatalogManagement/v4/productOffering -H "$T" -H "$J" -d '{
  "name":"Premium upgrade","lifecycleStatus":"Active","businessValue":120,"channel":[{"id":"web"}],
  "propensityModel":{"version":"v1","intercept":-2.5,"coefficients":{"tenureMonths":0.04}}}' | jq -r .id)

curl -s localhost:8082/tmf-api/promotionManagement/v4/promotion -H "$T" -H "$J" -d '{
  "name":"Loyal upgrade","lifecycleStatus":"active","channel":[{"id":"web"}],
  "eligibilityRule":"tenureMonths >= 12","controlGroupPercent":5,"maxImpressionsPerDay":3,
  "action":[{"actionObjectId":"'$OFFER'"}]}'

curl -s localhost:8083/tmf-api/recommendationManagement/v4/recommendation -H "$T" -H "$J" -d '{
  "relatedParty":[{"id":"party-42"}],"channel":{"id":"web"},
  "characteristic":[{"name":"tenureMonths","value":24}]}' | jq

curl -s localhost:8084/tmf-api/partyInteractionManagement/v4/partyInteraction -H "$T" -H "$J" \
  -H 'Idempotency-Key: party-42-1' -d '{
  "relatedParty":[{"id":"party-42"}],"channel":[{"id":"web"}],"decisionId":"<recommendation id>",
  "productOffering":{"id":"'$OFFER'"},"outcome":"ACCEPTED"}'
```

## Build and test

```bash
mvn verify
```

Tests use H2 (PostgreSQL mode) and an embedded Kafka broker, so no external services are needed.

## Deploy

See [docs/deployment.md](docs/deployment.md) for kind, Amazon EKS and OpenShift, and for the optional service mesh.

## Known limitations of this first milestone

- **No authentication yet.** The APIs trust the `X-Tenant-Id` header. OAuth2/OIDC with per-tenant scopes is the
  next security step and must be in place before any shared or production deployment.
- Learning statistics and contact history live in each decision replica's memory, rebuilt from events on start.
  This is fine for the MVP; a Redis-compatible store behind the same SPI replaces it at scale.
- The decision log is published asynchronously without an outbox, so a broker outage can lose log entries
  (decisions still succeed).
- OpenAPI/AsyncAPI files are hand-written alongside the code; generated contract tests are not in place yet.
- Feature store, outbound orchestration, uplift models, the constrained optimizer and an admin UI are later phases
  (see the roadmap in the research document).
