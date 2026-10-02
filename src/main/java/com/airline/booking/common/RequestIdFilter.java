package com.airline.booking.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a short correlation id in the logging MDC for the duration of each request,
 * and echoes it back as {@code X-Request-Id}.
 *
 * <p>The id also becomes the {@code traceId} on every {@link ApiError}, so a
 * customer reporting "I got a 500 with traceId b7f1c2e4" hands you the exact log
 * line — without the response ever exposing a stack trace.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String MDC_KEY = "requestId";
  public static final String HEADER = "X-Request-Id";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = request.getHeader(HEADER);

    if(requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString().substring(0, 8);
    }

    MDC.put(MDC_KEY, requestId);
    response.setHeader(HEADER, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      // MUST be in a finally block. Servlet threads are pooled and reused;
      // leaving the id behind makes the next request log under the previous
      // request's id, which is worse than having no id at all
      MDC.remove(MDC_KEY);
    }
  }

  /** Current request's id, or a generated fallback when there is no MDC value. */
  public static String currentOrFallback() {
    String id = MDC.get(MDC_KEY);
    return id != null ? id : UUID.randomUUID().toString().substring(0, 8);
  }
}
