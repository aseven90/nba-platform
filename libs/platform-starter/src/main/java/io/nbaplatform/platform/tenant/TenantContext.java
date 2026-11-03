package io.nbaplatform.platform.tenant;

/** Holds the tenant of the request being processed on the current thread. */
public final class TenantContext {

    public static final String HEADER = "X-Tenant-Id";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static String require() {
        String tenant = CURRENT.get();
        if (tenant == null) {
            throw new IllegalStateException("No tenant bound to the current thread");
        }
        return tenant;
    }

    static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    static void clear() {
        CURRENT.remove();
    }
}
