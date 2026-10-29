package com.hotelbooking.booking.security;

import com.hotelbooking.common.trace.TraceConstants;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdMdcFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, java.io.IOException {
		String traceId = request.getHeader(TraceConstants.HEADER);
		if (traceId != null && !traceId.isBlank()) {
			MDC.put(TraceConstants.MDC_TRACE_ID, traceId);
		}
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.clear();
		}
	}

}
