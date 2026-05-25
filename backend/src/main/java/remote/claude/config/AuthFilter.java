package remote.claude.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);
    private final ClaudeCliConfig config;

    public AuthFilter(ClaudeCliConfig config) {
        this.config = config;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {

        String token = config.getAuthToken();
        // Skip auth if no token configured
        if (token == null || token.isEmpty()) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // Allow static resources
        if (path.equals("/") || path.startsWith("/assets/") || path.startsWith("/api/config")
                || path.startsWith("/api/models")) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Protect /api/* endpoints
        if (path.startsWith("/api/")) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.equals("Bearer " + token)) {
                response.setStatus(401);
                response.setContentType("application/json; charset=utf-8");
                response.getWriter().write("{\"error\":\"Unauthorized. Set Authorization: Bearer <token> header\"}");
                return;
            }
        }

        chain.doFilter(servletRequest, servletResponse);
    }
}