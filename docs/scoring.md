# Propensity scoring and explanations

The `propensity-scoring` stage gives every candidate a propensity: the probability that the party responds to the
action. Each offering in the catalog can carry a `propensityModel`; its `type` picks the **scorer plugin** that
evaluates it.

| `type` | Scorer | Where it runs | Explanation |
|---|---|---|---|
| `logistic` (default) | `LogisticScorer` | In the decision service | Every feature's weight, value and contribution to the log-odds |
| `remote` | `RemoteModelScorer` | Model server (Open Inference Protocol / KServe v2) | The input values sent to the model |
| _(no model)_ | | | The default prior (`MODEL_DEFAULT_PRIOR`) |

Each distinct model is scored once per decision. Remote models run in parallel on virtual threads under one
scoring budget (`nba.decision.plugins.propensity-scoring.timeout`, 40 ms by default), inside the 80 ms decision
deadline.

## Logistic models

```json
"propensityModel": {
  "version": "v3",
  "intercept": -2.0,
  "coefficients": { "tenureMonths": 0.05, "isGold": 1.5 }
}
```

`p = sigmoid(intercept + sum(coefficient[f] * attribute[f]))`. Numbers, `true`/`false` and numeric strings are
used as is; anything missing or non-numeric counts as 0.

## Models served by a model server

Models trained in Python (scikit-learn, XGBoost, LightGBM, ...) are served by any server that speaks the
[Open Inference Protocol](https://kserve.github.io/website/latest/modelserving/data_plane/v2_protocol/): KServe,
Seldon MLServer, NVIDIA Triton.

```json
"propensityModel": {
  "type": "remote",
  "name": "churn-retention",
  "version": "3",
  "features": ["tenureMonths", "monthlySpend", "complaints90d"]
}
```

The decision service calls `POST {base-url}/v2/models/churn-retention/versions/3/infer` (without `/versions/...`
when `version` is empty) with one FP64 tensor `input-0` of shape `[1, n]`, filled from the decision attributes in
`features` order. The first output must hold `[p]` or class probabilities `[p0, p1]` (scikit-learn
`predict_proba`); the last value is the propensity.

```yaml
nba:
  decision:
    plugins:
      remote-model:
        enabled: true                  # or NBA_MODEL_SERVER_ENABLED=true
        base-url: http://models:8080   # or NBA_MODEL_SERVER_URL
        timeout: 30ms                  # per HTTP call
        circuit-breaker:               # per model
          failure-rate-threshold: 50
          minimum-number-of-calls: 20
          wait-in-open-state: 10s
```

Transport security between the decision service and the model server is left to the service mesh (mTLS), in
line with the platform principles.

## When scoring fails

A model is replaced by the default prior **for the actions that use it only** when:

| Reason code | Cause |
|---|---|
| `SCORER_UNAVAILABLE` | No scorer plugin for the model `type` (for example `remote` while the remote scorer is off) |
| `SCORING_ERROR` | The scorer threw, or the model server returned an error |
| `SCORING_TIMEOUT` | The model did not answer within the scoring budget |
| `SCORING_INVALID_OUTPUT` | The result was not a number between 0 and 1 |
| `CIRCUIT_OPEN` | The model kept failing, so calls are paused for `wait-in-open-state` |

The affected items carry `SCORING_FALLBACK` plus the cause in `reasonCode`, and `explanation.scoringFallback`
names the cause. Eligibility rules, consent, contact caps and control groups still apply, so the decision stays
safe and mostly personalised. The metric `nba.scoring.fallback{scorer,reason}` counts every fallback; logs warn at
most once per model and cause every 30 seconds.

To treat any scoring failure as a failed decision instead, set
`nba.decision.plugins.propensity-scoring.on-failure: safe-default`. The API then returns the tenant's safe default
actions with `fallback: true` and `fallbackReason: SCORING_FAILED`.

## The safe default answer

When the whole pipeline fails, misses its deadline, or the bulkhead or circuit breaker refuses it, the API returns
the tenant's `nba.decision.safe-defaults` with `fallback: true` and `fallbackReason`
(`PIPELINE_ERROR`, `DEADLINE_EXCEEDED`, `SCORING_FAILED`, `BULKHEAD_FULL`, `CIRCUIT_OPEN`, `INTERRUPTED`). It is
still a decision about a person, so:

- a party with `optOut = true`, or `consent.<channel> = false` (or no consent when explicit consent is required),
  gets no actions;
- configured offerings the service knows to be retired, expired or not offered on the channel are skipped;
- offerings the service has not loaded yet (for example right after start-up) are still returned, because an
  operator listed them.

With `?explain=true`, skipped safe defaults appear in `suppressedItem` with stage `safe-default`.

## Explanations

`POST /recommendation?explain=true` adds an `explanation` to every item:

```json
"explanation": {
  "scorer": "logistic",
  "modelVersion": "v3",
  "modelPropensity": 0.142,
  "propensity": 0.151,
  "value": 100.0,
  "priorityLever": 1.0,
  "priority": 15.1,
  "contributions": [
    { "feature": "tenureMonths", "value": 24.0, "weight": 0.05, "contribution": 1.2 }
  ]
}
```

`modelPropensity` is what the model said; `propensity` is what arbitration used after learning (Thompson sampling)
adjusted it; `priority = propensity x value x priorityLever`. Up to five contributions are returned, largest
effect first.

The decision log (`nba.decision.made`) records the same explanation for every item, without the attribute
`value`s (privacy by design: the log explains the decision without copying the party's data).

## Writing a scorer plugin

Implement `io.nbaplatform.decision.spi.PropensityScorer` and register it as a bean (for example from your own
auto-configuration). Return a `Score` with the propensity and, if the model can say, `FeatureContribution`s. Throw
on failure; the stage turns exceptions into fallbacks. Return `true` from `remote()` if the scorer does I/O, so it
runs in parallel under the scoring budget.
