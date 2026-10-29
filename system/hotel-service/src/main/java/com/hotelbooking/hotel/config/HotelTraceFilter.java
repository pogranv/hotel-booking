package com.hotelbooking.hotel.config;

import java.io.IOException;

import com.hotelbooking.common.trace.TraceConstants;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HotelTraceFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		try {
			String traceId = request.getHeader(TraceConstants.HEADER);
			if (traceId != null && !traceId.isBlank()) {
				MDC.put(TraceConstants.MDC_TRACE_ID, traceId);
			}
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.clear();
		}
	}

}
