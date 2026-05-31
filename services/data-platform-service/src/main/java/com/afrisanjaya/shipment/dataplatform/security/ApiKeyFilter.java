package com.afrisanjaya.shipment.dataplatform.security;

import com.afrisanjaya.shipment.dataplatform.domain.entity.Tenant;
import com.afrisanjaya.shipment.dataplatform.domain.repository.TenantRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyFilter implements Filter {

    private final TenantRepository tenantRepository;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String uri = req.getRequestURI();
        String method = req.getMethod();

        if (uri.contains("/health") || uri.contains("/actuator")
                || uri.contains("/swagger") || uri.contains("/v3/api-docs")
                || uri.contains("/webjars") || uri.contains("/platform")
                || "OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        if ("POST".equalsIgnoreCase(method) && uri.equals("/api/v1/tenants")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing or invalid Authorization header. Use: Bearer <api-key>");
            return;
        }

        String apiKey = authHeader.substring(7);
        Optional<Tenant> tenant = tenantRepository.findByApiKey(apiKey);

        if (tenant.isEmpty() || !tenant.get().isActive()) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or inactive API key");
            return;
        }

        req.setAttribute("tenant", tenant.get());
        chain.doFilter(request, response);
    }
}
