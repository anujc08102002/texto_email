package com.texto.emailplatform.common.logging;

import com.texto.emailplatform.common.observability.RequestCorrelation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = RequestCorrelation.resolveRequestId(request.getHeader(RequestCorrelation.REQUEST_ID_HEADER));
        String traceId = RequestCorrelation.resolveTraceId(
                request.getHeader(RequestCorrelation.TRACE_ID_HEADER),
                request.getHeader(RequestCorrelation.TRACEPARENT_HEADER),
                requestId
        );
        RequestCorrelation.bind(requestId, traceId);
        response.setHeader(RequestCorrelation.REQUEST_ID_HEADER, requestId);
        if (traceId != null) {
            response.setHeader(RequestCorrelation.TRACE_ID_HEADER, traceId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestCorrelation.clear();
        }
    }
}
