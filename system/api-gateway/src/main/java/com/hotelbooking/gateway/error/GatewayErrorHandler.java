package com.hotelbooking.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.MDC;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.hotelbooking.common.error.ErrorDto;
import com.hotelbooking.common.error.ErrorDtoFactory;
import com.hotelbooking.common.trace.TraceConstants;

import reactor.core.publisher.Mono;

@Component
public class GatewayErrorHandler implements ErrorWebExceptionHandler, Ordered {

	private static final String NOT_FOUND_MESSAGE = "Маршрут не найден";

	private static final String UNAVAILABLE_MESSAGE = "Сервис недоступен";

	private final ObjectMapper objectMapper;

	public GatewayErrorHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public int getOrder() {
		return -1;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
		if (exchange.getResponse().isCommitted()) {
			return Mono.error(ex);
		}
		int status = isRouteNotFound(ex) ? HttpStatus.NOT_FOUND.value() : HttpStatus.SERVICE_UNAVAILABLE.value();
		String message = status == HttpStatus.NOT_FOUND.value() ? NOT_FOUND_MESSAGE : UNAVAILABLE_MESSAGE;
		Object attribute = exchange.getAttribute(TraceConstants.MDC_TRACE_ID);
		if (attribute instanceof String traceId) {
			MDC.put(TraceConstants.MDC_TRACE_ID, traceId);
		}
		else {
			MDC.remove(TraceConstants.MDC_TRACE_ID);
		}
		try {
			String path = exchange.getRequest().getURI().getRawPath();
			ErrorDto body = ErrorDtoFactory.of(status, message, path);
			byte[] bytes = this.objectMapper.writeValueAsBytes(body);
			ServerHttpResponse response = exchange.getResponse();
			response.setStatusCode(HttpStatus.valueOf(status));
			response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
			DataBuffer buffer = response.bufferFactory().wrap(bytes);
			return response.writeWith(Mono.just(buffer));
		}
		catch (JsonProcessingException jsonException) {
			return Mono.error(jsonException);
		}
		finally {
			MDC.remove(TraceConstants.MDC_TRACE_ID);
		}
	}

	private static boolean isRouteNotFound(Throwable ex) {
		Throwable current = ex;
		while (current != null) {
			if (current instanceof ResponseStatusException responseStatus
					&& responseStatus.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

}
