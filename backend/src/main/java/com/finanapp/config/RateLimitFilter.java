package com.finanapp.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter implements Filter {

    private static final int MAX_REQUESTS_PER_MINUTE = 120;
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private volatile long currentMinute = System.currentTimeMillis() / 60000;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        String path = httpReq.getRequestURI();

        // Only rate-limit API endpoints
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        long minute = System.currentTimeMillis() / 60000;
        if (minute != currentMinute) {
            currentMinute = minute;
            requestCounts.clear();
        }

        String clientKey = httpReq.getRemoteAddr();
        AtomicInteger count = requestCounts.computeIfAbsent(clientKey, k -> new AtomicInteger(0));
        int current = count.incrementAndGet();

        HttpServletResponse httpRes = (HttpServletResponse) response;
        httpRes.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_MINUTE));
        httpRes.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, MAX_REQUESTS_PER_MINUTE - current)));

        if (current > MAX_REQUESTS_PER_MINUTE) {
            httpRes.setStatus(429);
            httpRes.setContentType("application/json");
            httpRes.getWriter().write("{\"error\":\"Rate limit exceeded. Max " + MAX_REQUESTS_PER_MINUTE + " requests/minute.\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
