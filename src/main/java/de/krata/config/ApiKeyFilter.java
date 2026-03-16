package de.krata.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Prüft optional den X-API-Key-Header. Nur aktiv, wenn krata.api-key gesetzt ist.
 */
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

    private final Optional<String> configuredApiKey;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (configuredApiKey.isEmpty() || configuredApiKey.get().isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getHeader("X-API-Key");
        if (!configuredApiKey.get().equals(key)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Fehlender oder ungültiger API-Key\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
