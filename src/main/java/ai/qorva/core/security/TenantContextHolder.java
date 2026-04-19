package ai.qorva.core.security;

/**
 * Thread-local holder for the current request's tenant ID.
 * Populated by JwtRequestFilter on every authenticated request and cleared after the filter chain completes.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static String getTenantId() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
