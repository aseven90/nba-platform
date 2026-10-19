package io.nbaplatform.platform.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** JSON Merge Patch (RFC 7386), the PATCH format TMF630 requires every API to support. */
public final class JsonMergePatch {

    private JsonMergePatch() {
    }

    /** Applies {@code patch} to {@code current}, validates the result and returns it as {@code type}. */
    public static <T> T apply(ObjectMapper mapper, Validator validator, T current, JsonNode patch, Class<T> type) {
        JsonNode merged = merge(mapper.valueToTree(current), patch);
        T result;
        try {
            result = mapper.treeToValue(merged, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid patch: " + e.getOriginalMessage());
        }
        Set<ConstraintViolation<T>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> first = violations.iterator().next();
            throw new IllegalArgumentException(first.getPropertyPath() + " " + first.getMessage());
        }
        return result;
    }

    static JsonNode merge(JsonNode target, JsonNode patch) {
        if (!patch.isObject()) {
            return patch;
        }
        ObjectNode result = target != null && target.isObject() ? ((ObjectNode) target).deepCopy() : patch.deepCopy();
        if (target == null || !target.isObject()) {
            result.removeAll();
        }
        Iterator<Map.Entry<String, JsonNode>> fields = patch.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().isNull()) {
                result.remove(field.getKey());
            } else {
                result.set(field.getKey(), merge(result.get(field.getKey()), field.getValue()));
            }
        }
        return result;
    }
}
