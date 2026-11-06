package io.nbaplatform.decision.plugins;

/** Turns decision attributes into model inputs. Missing or non-numeric values become 0. */
final class Features {

    private Features() {
    }

    static double numeric(Object value) {
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) ? d : 0.0;
        }
        if (value instanceof Boolean b) {
            return b ? 1.0 : 0.0;
        }
        if (value instanceof String s) {
            if (s.equalsIgnoreCase("true")) {
                return 1.0;
            }
            try {
                double d = Double.parseDouble(s.trim());
                return Double.isFinite(d) ? d : 0.0;
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
