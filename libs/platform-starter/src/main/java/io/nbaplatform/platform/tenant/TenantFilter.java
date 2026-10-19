package io.nbaplatform.platform.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nbaplatform.platform.web.TmfError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Requires a valid {@value TenantContext#HEADER} on every TMF API call and binds it to the request thread.
 * Actuator and other non-API paths are not tenant-scoped.
 */
public class TenantFilter extends OncePerRequestFilter {

    private static final Pattern TENANT_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final ObjectMapper objectMapper;

    public TenantFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/tmf-api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String tenant = request.getHeader(TenantContext.HEADER);
        if (tenant == null || !TENANT_ID.matcher(tenant).matches()) {
            response.setStatus(400);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), TmfError.of(400, "missingTenant",
                    "Header " + TenantContext.HEADER + " is required and must match " + TENANT_ID.pattern()));
            return;
        }
        TenantContext.set(tenant);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
