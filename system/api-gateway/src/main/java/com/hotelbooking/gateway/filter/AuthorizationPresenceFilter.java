package com.hotelbooking.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.hotelbooking.common.error.ErrorDto;
import com.hotelbooking.common.error.ErrorDtoFactory;

import reactor.core.publisher.Mono;

@Component
public class AuthorizationPresenceFilter implements GlobalFilter, Ordered {

	private static final String UNAUTHORIZED_MESSAGE = "Требуется заголовок Authorization";

	private static final String[] PREFIXES = { "/api/user", "/api/booking", "/api/bookings", "/api/hotels",
			"/api/rooms" };

	private final ObjectMapper objectMapper;

	public AuthorizationPresenceFilter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public int getOrder() {
		return -2;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String path = pathForMatch(exchange.getRequest().getURI().getRawPath());
		if (isPublic(exchange, path) || !isGatewayPrefix(path)) {
			return chain.filter(exchange);
		}
		String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (authorization != null && !authorization.isBlank()) {
			return chain.filter(exchange);
		}
		return writeUnauthorized(exchange);
	}

	private static boolean isPublic(ServerWebExchange exchange, String path) {
		if (!HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
			return false;
		}
		return "/api/user/register".equals(path) || "/api/user/auth".equals(path);
	}

	private static boolean isGatewayPrefix(String path) {
		for (String prefix : PREFIXES) {
			if (path.equals(prefix) || path.startsWith(prefix + "/")) {
				return true;
			}
		}
		return false;
	}

	private Mono<Void> writeUnauthorized(ServerWebExchange exchange) {
		String path = exchange.getRequest().getURI().getRawPath();
		ErrorDto body = ErrorDtoFactory.of(HttpStatus.UNAUTHORIZED.value(), UNAUTHORIZED_MESSAGE, path);
		byte[] bytes;
		try {
			bytes = this.objectMapper.writeValueAsBytes(body);
		}
		catch (JsonProcessingException ex) {
			return Mono.error(ex);
		}
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(HttpStatus.UNAUTHORIZED);
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
