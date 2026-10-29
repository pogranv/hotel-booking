package com.hotelbooking.gateway.filter;

import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.hotelbooking.common.trace.TraceConstants;

import reactor.core.publisher.Mono;

@Component
public class TraceIdFilter implements GlobalFilter, WebFilter, Ordered {

	private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

	private static final String APPLIED = TraceIdFilter.class.getName() + ".applied";

	@Override
	public int getOrder() {
		return -4;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return apply(exchange, chain::filter);
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		return apply(exchange, chain::filter);
	}

	private Mono<Void> apply(ServerWebExchange exchange, Function<ServerWebExchange, Mono<Void>> next) {
		if (Boolean.TRUE.equals(exchange.getAttribute(APPLIED))) {
			return next.apply(exchange);
		}
		exchange.getAttributes().put(APPLIED, Boolean.TRUE);

		String incoming = exchange.getRequest().getHeaders().getFirst(TraceConstants.HEADER);
		String traceId = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString().replace("-", "")
				: incoming;
		exchange.getAttributes().put(TraceConstants.MDC_TRACE_ID, traceId);
		MDC.put(TraceConstants.MDC_TRACE_ID, traceId);

		ServerWebExchange traced = exchange.mutate()
			.request(builder -> builder.headers(headers -> headers.set(TraceConstants.HEADER, traceId)))
			.build();
		String method = traced.getRequest().getMethod() == null ? null : traced.getRequest().getMethod().name();
		String rawPath = traced.getRequest().getURI().getRawPath();
		return next.apply(traced).doFinally(signalType -> {
			MDC.put(TraceConstants.MDC_TRACE_ID, traceId);
			HttpStatusCode status = traced.getResponse().getStatusCode();
			Integer statusCode = status == null ? null : status.value();
			log.info("{} {} {} {}", method, rawPath, statusCode, traceId);
			MDC.remove(TraceConstants.MDC_TRACE_ID);
		});
	}

}
