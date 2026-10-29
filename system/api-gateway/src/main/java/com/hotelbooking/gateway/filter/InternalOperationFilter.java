package com.hotelbooking.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.hotelbooking.common.error.ErrorDto;
import com.hotelbooking.common.error.ErrorDtoFactory;

import reactor.core.publisher.Mono;

@Component
public class InternalOperationFilter implements GlobalFilter, Ordered {

	private static final String NOT_FOUND_MESSAGE = "Маршрут не найден";

	private final ObjectMapper objectMapper;

	public InternalOperationFilter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public int getOrder() {
		return -3;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String path = pathForMatch(exchange.getRequest().getURI().getRawPath());
		if (path.endsWith("/confirm-availability") || path.endsWith("/release")) {
			return writeNotFound(exchange);
		}
		return chain.filter(exchange);
	}

	private Mono<Void> writeNotFound(ServerWebExchange exchange) {
		String path = exchange.getRequest().getURI().getRawPath();
		ErrorDto body = ErrorDtoFactory.of(HttpStatus.NOT_FOUND.value(), NOT_FOUND_MESSAGE, path);
		byte[] bytes;
		try {
			bytes = this.objectMapper.writeValueAsBytes(body);
		}
		catch (JsonProcessingException ex) {
			return Mono.error(ex);
		}
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(HttpStatus.NOT_FOUND);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		DataBuffer buffer = response.bufferFactory().wrap(bytes);
		return response.writeWith(Mono.just(buffer));
	}

	private static String pathForMatch(String rawPath) {
		if (rawPath != null && rawPath.length() > 1 && rawPath.endsWith("/")) {
			return rawPath.substring(0, rawPath.length() - 1);
		}
		return rawPath == null ? "" : rawPath;
	}

}
