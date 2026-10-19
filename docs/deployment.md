# Deployment

Every service is an independent container image and Helm chart. Charts share one library chart
(`deploy/helm/nba-common`) so security, probes, autoscaling and disruption budgets are identical everywhere.

| Path | Purpose |
|---|---|
| `deploy/compose` | Local stack: PostgreSQL, Redpanda (Kafka API) and all services |
| `deploy/helm/<service>` | One chart per service |
| `deploy/helm/environments` | Overlays for `kind`, `eks` and `openshift` |
| `deploy/kafka/topics.yaml` | Strimzi / AMQ Streams `KafkaTopic` resources |

## Dependencies the platform expects

Only open protocols, so managed or self-hosted equivalents both work:

- **Kafka API**: Strimzi or AMQ Streams on Kubernetes/OpenShift, Amazon MSK, Confluent, Redpanda, Azure Event Hubs.
- **PostgreSQL**: one database per service (CloudNativePG, Crunchy, Amazon RDS, Cloud SQL, Azure Database).
- **OpenTelemetry collector** at `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` for traces; Prometheus scrapes `/actuator/prometheus`.

## Local Kubernetes (kind)

```bash
kind create cluster --name nba
for m in services/action-catalog-service services/campaign-service services/decision-service/decision-app services/interaction-service; do
  name=$(basename $m); [ "$name" = decision-app ] && name=decision-service
  docker build --build-arg MODULE=$m -t ghcr.io/aseven90/nba-platform/$name:0.1.0 .
  kind load docker-image ghcr.io/aseven90/nba-platform/$name:0.1.0 --name nba
done
# Install Kafka (e.g. Strimzi) and PostgreSQL, apply deploy/kafka/topics.yaml, create the <service>-db secrets, then:
for c in action-catalog-service campaign-service decision-service interaction-service; do
  helm dependency build deploy/helm/$c
  helm upgrade --install $c deploy/helm/$c -f deploy/helm/environments/kind.yaml
done
```

## Amazon EKS

Use `-f deploy/helm/environments/eks.yaml`, which exposes the TMF APIs through the AWS Load Balancer Controller.
Point `KAFKA_BOOTSTRAP_SERVERS` and `DB_URL` at MSK and RDS, or at in-cluster Strimzi and CloudNativePG.

## OpenShift

Use `-f deploy/helm/environments/openshift.yaml`:

- Pods run under the `restricted-v2` SCC. The chart does not pin `runAsUser`; the image runs as any UID in group 0.
- Root filesystem is read-only, all capabilities are dropped, `seccompProfile` is `RuntimeDefault`.
- APIs are exposed through edge-terminated `Route`s.
- Use AMQ Streams (Strimzi) for Kafka and Crunchy or CloudNativePG operators for PostgreSQL.

## Scaling

- Decision, catalog, campaign and interaction services are stateless and scale horizontally with the HPA (CPU by
  default; add request-rate metrics through `autoscaling.extraMetrics`).
- Kafka partition counts cap consumer parallelism; `deploy/kafka/topics.yaml` sizes them for ~2,000 decisions/s.
- Decision replicas each hold the full campaign/offering snapshot in memory and rebuild it from the compacted
  topics on start. Size memory for the snapshot, not for request volume.

## Service mesh (optional)

The platform does not need a mesh: timeouts, circuit breaking and bulkheads are built in. To add mTLS and
traffic shifting, annotate pods through `podAnnotations`:

```yaml
# Istio ambient mode (no sidecars)
podAnnotations:
  istio.io/dataplane-mode: ambient
# or Linkerd
podAnnotations:
  linkerd.io/inject: enabled
```

Do not enable mesh-level retries for the decision API: the decision path has a short deadline and no retries by
design, and stacked retries cause storms.
