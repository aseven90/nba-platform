# Eligibility rules

A campaign's `eligibilityRule` decides whether a party may receive the campaign's actions. It is evaluated
against the decision attributes: the request's `characteristic` list merged with stored features (request values
win).

## Syntax

Rules are a restricted [Spring Expression Language](https://docs.spring.io/spring-framework/reference/core/expressions.html)
dialect. Attribute names are used directly.

| Construct | Example |
|---|---|
| Comparison | `tenureMonths >= 12`, `segment == 'gold'`, `segment != 'churned'` |
| Boolean logic | `a > 1 and (b == 'x' or not c)` |
| Arithmetic | `monthlySpend * 12 > 1000` |
| Membership | `{'gold','platinum'}.contains(segment)` is **not** allowed (no method calls); use `segment == 'gold' or segment == 'platinum'` |

Method calls, type references (`T(...)`), bean references and assignment are rejected.

## Evaluation

- A rule that returns `true` keeps the campaign's actions; `false` suppresses them with reason `RULE_NOT_MET`.
- A rule that cannot be evaluated, typically because an attribute it uses is missing, fails closed with reason
  `RULE_NOT_EVALUABLE`. Callers should send every attribute their rules depend on.
- The campaign service rejects rules that do not parse (`400`).

## Reserved attributes

| Attribute | Effect |
|---|---|
| `optOut` = `true` | Suppresses all actions (`OPTED_OUT`). |
| `consent.<channel>` = `false` | Suppresses all actions on that channel (`NO_CHANNEL_CONSENT`). With `nba.decision.plugins.consent.require-explicit=true`, a missing value also suppresses. |
