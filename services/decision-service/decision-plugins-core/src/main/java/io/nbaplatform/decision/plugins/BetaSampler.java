package io.nbaplatform.decision.plugins;

import java.util.random.RandomGenerator;

/** Draws from Beta(a, b) via two Gamma draws (Marsaglia and Tsang). */
final class BetaSampler {

    private BetaSampler() {
    }

    static double sample(RandomGenerator random, double a, double b) {
        double x = gamma(random, a);
        double y = gamma(random, b);
        return x / (x + y);
    }

    static double gamma(RandomGenerator random, double shape) {
        if (shape < 1.0) {
            // Boost: Gamma(k) = Gamma(k + 1) * U^(1/k)
            return gamma(random, shape + 1.0) * Math.pow(random.nextDouble(), 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x;
            double v;
            do {
                x = random.nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0);
            v = v * v * v;
            double u = random.nextDouble();
            if (u < 1.0 - 0.0331 * x * x * x * x || Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) {
                return d * v;
            }
        }
    }
}
