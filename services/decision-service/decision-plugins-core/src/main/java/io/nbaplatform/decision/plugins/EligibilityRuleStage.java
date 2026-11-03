package io.nbaplatform.decision.plugins;

import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionStage;
import io.nbaplatform.decision.spi.Stage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Evaluates each campaign's eligibility rule against the decision attributes. Rules use a restricted SpEL
 * dialect: attribute names, literals, comparison, boolean and arithmetic operators only; no method calls,
 * type references or assignment. A rule that cannot be evaluated (for example, a missing attribute) fails
 * closed.
 */
public class EligibilityRuleStage implements DecisionStage {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    @Override
    public Stage stage() {
        return Stage.ELIGIBILITY;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String name() {
        return "eligibility-rules";
    }

    @Override
    public void apply(DecisionContext context) {
        Map<String, Object> attributes = context.request().attributes();
        EvaluationContext evaluation = SimpleEvaluationContext.forPropertyAccessors(new MapAccessor())
                .withAssignmentDisabled()
                .build();
        Map<String, String> verdicts = new java.util.HashMap<>();
        context.suppressIf(c -> {
            String rule = c.campaign().eligibilityRule();
            if (rule == null || rule.isBlank()) {
                return null;
            }
            return verdicts.computeIfAbsent(c.campaign().id(), id -> evaluate(rule, attributes, evaluation));
        });
    }

    /** Returns null when eligible, otherwise a reason code. */
    String evaluate(String rule, Map<String, Object> attributes, EvaluationContext evaluation) {
        try {
            Expression expression = cache.computeIfAbsent(rule, PARSER::parseExpression);
            Boolean result = expression.getValue(evaluation, attributes, Boolean.class);
            return Boolean.TRUE.equals(result) ? null : "RULE_NOT_MET";
        } catch (ParseException | EvaluationException | IllegalStateException e) {
            return "RULE_NOT_EVALUABLE";
        }
    }
}
