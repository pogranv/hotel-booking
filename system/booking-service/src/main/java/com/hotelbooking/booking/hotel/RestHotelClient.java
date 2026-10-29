package com.hotelbooking.booking.hotel;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.booking.hotel.dto.ConfirmAvailabilityCommand;
import com.hotelbooking.booking.hotel.dto.ReleaseRoomCommand;
import com.hotelbooking.booking.hotel.dto.RoomRecommendation;
import com.hotelbooking.common.trace.TraceConstants;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RestHotelClient implements HotelClient {

	private static final String HOTEL_CALLS = "hotelCalls";

	private static final String HOTEL_RELEASE = "hotelRelease";

	private static final TypeReference<List<RoomRecommendation>> ROOMS = new TypeReference<>() {
	};

	private final RestClient hotelRestClient;

	private final ExecutorService hotelClientExecutor;

	private final RetryRegistry retryRegistry;

	private final CircuitBreakerRegistry circuitBreakerRegistry;

	private final TimeLimiterRegistry timeLimiterRegistry;

	private final ObjectMapper objectMapper;

	public RestHotelClient(RestClient hotelRestClient, ExecutorService hotelClientExecutor, RetryRegistry retryRegistry,
			CircuitBreakerRegistry circuitBreakerRegistry, TimeLimiterRegistry timeLimiterRegistry,
			ObjectMapper objectMapper) {
		this.hotelRestClient = hotelRestClient;
		this.hotelClientExecutor = hotelClientExecutor;
		this.retryRegistry = retryRegistry;
		this.circuitBreakerRegistry = circuitBreakerRegistry;
		this.timeLimiterRegistry = timeLimiterRegistry;
		this.objectMapper = objectMapper;
	}

	@Override
	public List<RoomRecommendation> recommend() {
		HttpHeaders forwarded = captureHeaders();
		return execute(HOTEL_CALLS, () -> doRecommend(forwarded));
	}

	@Override
	public HotelConfirmStatus confirm(long roomId, ConfirmAvailabilityCommand command) {
		HttpHeaders forwarded = captureHeaders();
		return execute(HOTEL_CALLS, () -> doConfirm(roomId, command, forwarded));
	}

	@Override
	public void release(long roomId, ReleaseRoomCommand command) {
		HttpHeaders forwarded = captureHeaders();
		execute(HOTEL_RELEASE, () -> {
			doRelease(roomId, command, forwarded);
			return null;
		});
	}

	private <T> T execute(String instanceName, java.util.function.Supplier<T> supplier) {
		TimeLimiter timeLimiter = this.timeLimiterRegistry.timeLimiter(instanceName);
		CircuitBreaker circuitBreaker = this.circuitBreakerRegistry.circuitBreaker(instanceName);
		Retry retry = this.retryRegistry.retry(instanceName);
		Callable<T> timed = timeLimiter
			.decorateFutureSupplier(() -> CompletableFuture.supplyAsync(supplier, this.hotelClientExecutor));
		Callable<T> guarded = circuitBreaker.decorateCallable(timed);
		Callable<T> retried = Retry.decorateCallable(retry, guarded);
		try {
			return retried.call();
		}
		catch (HotelRejectedStatusException ex) {
			throw ex;
		}
		catch (Exception ex) {
			HotelRejectedStatusException rejected = findRejected(ex);
			if (rejected != null) {
				throw rejected;
			}
			if (ex instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new HotelClientException(ex);
		}
	}

	private List<RoomRecommendation> doRecommend(HttpHeaders forwarded) {
		return this.hotelRestClient.get()
			.uri("/api/rooms/recommend")
			.headers(headers -> applyHeaders(headers, forwarded))
			.exchange((request, response) -> {
				byte[] body = readBytes(response);
				int status = response.getStatusCode().value();
				if (status == 200) {
					return readRooms(body);
				}
				if (status >= 500) {
					throw new HotelTransientException("HTTP " + status);
				}
				throw new HotelRejectedStatusException("HTTP " + status);
			});
	}

	private HotelConfirmStatus doConfirm(long roomId, ConfirmAvailabilityCommand command, HttpHeaders forwarded) {
		return this.hotelRestClient.post()
			.uri("/api/rooms/{roomId}/confirm-availability", roomId)
			.headers(headers -> applyHeaders(headers, forwarded))
			.body(command)
			.exchange((request, response) -> {
				readBytes(response);
				int status = response.getStatusCode().value();
				if (status == 200) {
					return HotelConfirmStatus.OK;
				}
				if (status == 409) {
					return HotelConfirmStatus.CONFLICT;
				}
				if (status == 404) {
					return HotelConfirmStatus.NOT_FOUND;
				}
				if (status >= 500) {
					throw new HotelTransientException("HTTP " + status);
				}
				if (status >= 400) {
					throw new HotelRejectedStatusException("HTTP " + status);
				}
				throw new HotelTransientException("HTTP " + status);
			});
	}

	private void doRelease(long roomId, ReleaseRoomCommand command, HttpHeaders forwarded) {
		this.hotelRestClient.post()
			.uri("/api/rooms/{roomId}/release", roomId)
			.headers(headers -> applyHeaders(headers, forwarded))
			.body(command)
			.exchange((request, response) -> {
				readBytes(response);
				int status = response.getStatusCode().value();
				if (status == 200) {
					return null;
				}
				if (status >= 500) {
					throw new HotelTransientException("HTTP " + status);
				}
				if (status >= 400) {
					throw new HotelRejectedStatusException("HTTP " + status);
				}
				throw new HotelTransientException("HTTP " + status);
			});
	}

	private List<RoomRecommendation> readRooms(byte[] body) throws IOException {
		if (body.length == 0) {
			return List.of();
		}
		List<RoomRecommendation> rooms = this.objectMapper.readValue(body, ROOMS);
		return rooms == null ? List.of() : rooms;
	}

	private static byte[] readBytes(org.springframework.http.client.ClientHttpResponse response) throws IOException {
		try (InputStream body = response.getBody()) {
			return body.readAllBytes();
		}
	}

	private static HttpHeaders captureHeaders() {
		HttpHeaders headers = new HttpHeaders();
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
			return headers;
		}
		HttpServletRequest request = servletAttributes.getRequest();
		copyFirst(headers, request, HttpHeaders.AUTHORIZATION);
		copyFirst(headers, request, TraceConstants.HEADER);
		return headers;
	}

	private static void applyHeaders(HttpHeaders target, HttpHeaders forwarded) {
		applyFirst(target, forwarded, HttpHeaders.AUTHORIZATION);
		applyFirst(target, forwarded, TraceConstants.HEADER);
	}

	private static void applyFirst(HttpHeaders target, HttpHeaders forwarded, String name) {
		String value = forwarded.getFirst(name);
		if (value != null && !value.isBlank()) {
			target.set(name, value);
		}
	}

	private static void copyFirst(HttpHeaders headers, HttpServletRequest request, String name) {
		String value = request.getHeader(name);
		if (value != null && !value.isBlank()) {
			headers.set(name, value);
		}
	}

	private static HotelRejectedStatusException findRejected(Throwable ex) {
		Throwable current = ex;
		while (current != null) {
			if (current instanceof HotelRejectedStatusException rejected) {
				return rejected;
			}
			current = current.getCause();
		}
		return null;
	}

}
