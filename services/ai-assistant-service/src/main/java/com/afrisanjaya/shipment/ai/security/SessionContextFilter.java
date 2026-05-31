package com.afrisanjaya.shipment.ai.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class SessionContextFilter implements Filter {

    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String uri = req.getRequestURI();
        if (uri.startsWith("/api/") && !uri.contains("/health")) {
            String userId = req.getHeader(USER_ID_HEADER);
            if (userId == null || userId.isBlank()) {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing " + USER_ID_HEADER + " header");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
