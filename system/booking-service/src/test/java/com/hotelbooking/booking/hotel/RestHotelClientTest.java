package com.hotelbooking.booking.hotel;

import java.time.LocalDate;
import java.util.List;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.hotelbooking.booking.hotel.dto.ConfirmAvailabilityCommand;
import com.hotelbooking.booking.hotel.dto.ReleaseRoomCommand;
import com.hotelbooking.booking.hotel.dto.RoomRecommendation;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
		"spring.cloud.discovery.client.simple.instances.hotel-service[0].uri=http://127.0.0.1:${wiremock.server.port}",
		"resilience4j.retry.instances.hotelCalls.max-attempts=4",
		"resilience4j.retry.instances.hotelRelease.max-attempts=4",
		"resilience4j.retry.instances.hotelCalls.wait-duration=40ms",
		"resilience4j.retry.instances.hotelRelease.wait-duration=40ms",
		"resilience4j.retry.instances.hotelCalls.enable-exponential-backoff=true",
		"resilience4j.retry.instances.hotelRelease.enable-exponential-backoff=true",
		"resilience4j.retry.instances.hotelCalls.exponential-backoff-multiplier=2",
		"resilience4j.retry.instances.hotelRelease.exponential-backoff-multiplier=2",
		"resilience4j.timelimiter.instances.hotelCalls.timeout-duration=200ms",
		"resilience4j.timelimiter.instances.hotelRelease.timeout-duration=200ms",
		"resilience4j.circuitbreaker.instances.hotelCalls.sliding-window-type=COUNT_BASED",
		"resilience4j.circuitbreaker.instances.hotelCalls.sliding-window-size=4",
		"resilience4j.circuitbreaker.instances.hotelCalls.minimum-number-of-calls=4",
		"resilience4j.circuitbreaker.instances.hotelCalls.failure-rate-threshold=50",
		"resilience4j.circuitbreaker.instances.hotelCalls.wait-duration-in-open-state=30s" })
@AutoConfigureWireMock(port = 0)
class RestHotelClientTest {

	@Autowired
	private HotelClient hotelClient;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@BeforeEach
	void resetState() {
		this.circuitBreakerRegistry.circuitBreaker("hotelCalls").reset();
		this.circuitBreakerRegistry.circuitBreaker("hotelRelease").reset();
		WireMock.reset();
	}

	@Test
	void recommendForwardsHeadersCapturedOnCallerThread() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer caller-token");
		request.addHeader("X-Trace-Id", "trace-caller");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
		stubFor(get(urlEqualTo("/api/rooms/recommend"))
			.willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("[]")));

		try {
			this.hotelClient.recommend();
		}
		finally {
			RequestContextHolder.resetRequestAttributes();
		}

		var logged = findAll(anyRequestedFor(anyUrl())).get(0);
		assertEquals("Bearer caller-token", logged.getHeader(HttpHeaders.AUTHORIZATION));
		assertEquals("trace-caller", logged.getHeader("X-Trace-Id"));
	}

	@Test
	void recommendCallsGetRoomsRecommend() {
		stubFor(get(urlEqualTo("/api/rooms/recommend")).willReturn(aResponse().withStatus(200)
			.withHeader("Content-Type", "application/json")
			.withBody("[{\"id\":15,\"hotelId\":1,\"number\":\"101\",\"available\":true,\"timesBooked\":2}]")));

		List<RoomRecommendation> rooms = this.hotelClient.recommend();

		assertEquals(1, rooms.size());
		assertEquals(15L, rooms.get(0).id());
		var requests = findAll(anyRequestedFor(anyUrl()));
		assertEquals(1, requests.size());
		assertEquals("GET", requests.get(0).getMethod().getName());
		assertEquals("/api/rooms/recommend", requests.get(0).getUrl());
	}

	@Test
	void confirmConflictIsNotRetriedOrRecorded() {
		CircuitBreaker calls = this.circuitBreakerRegistry.circuitBreaker("hotelCalls");
		int failedBefore = calls.getMetrics().getNumberOfFailedCalls();
		stubFor(post(urlEqualTo("/api/rooms/7/confirm-availability")).willReturn(aResponse().withStatus(409)));

		HotelConfirmStatus status = this.hotelClient.confirm(7, command());

		assertEquals(HotelConfirmStatus.CONFLICT, status);
		assertEquals(1, findAll(postRequestedFor(urlEqualTo("/api/rooms/7/confirm-availability"))).size());
		assertEquals(failedBefore, calls.getMetrics().getNumberOfFailedCalls());
		assertNotEquals(CircuitBreaker.State.OPEN, calls.getState());
	}

	@Test
	void confirmNotFoundIsNotRetriedOrRecorded() {
		CircuitBreaker calls = this.circuitBreakerRegistry.circuitBreaker("hotelCalls");
		int failedBefore = calls.getMetrics().getNumberOfFailedCalls();
		stubFor(post(urlEqualTo("/api/rooms/7/confirm-availability")).willReturn(aResponse().withStatus(404)));

		HotelConfirmStatus status = this.hotelClient.confirm(7, command());

		assertEquals(HotelConfirmStatus.NOT_FOUND, status);
		assertEquals(1, findAll(postRequestedFor(urlEqualTo("/api/rooms/7/confirm-availability"))).size());
		assertEquals(failedBefore, calls.getMetrics().getNumberOfFailedCalls());
		assertNotEquals(CircuitBreaker.State.OPEN, calls.getState());
	}

	@Test
	void confirmOther4xxIsNotRetriedCompensatedOrRecorded() {
		CircuitBreaker calls = this.circuitBreakerRegistry.circuitBreaker("hotelCalls");
		int failedBefore = calls.getMetrics().getNumberOfFailedCalls();
		stubFor(post(urlEqualTo("/api/rooms/7/confirm-availability")).willReturn(aResponse().withStatus(400)));

		assertThrows(HotelRejectedStatusException.class, () -> this.hotelClient.confirm(7, command()));

		assertEquals(1, findAll(postRequestedFor(urlEqualTo("/api/rooms/7/confirm-availability"))).size());
		assertEquals(0, findAll(postRequestedFor(urlEqualTo("/api/rooms/7/release"))).size());
		assertEquals(failedBefore, calls.getMetrics().getNumberOfFailedCalls());
	}

	@Test
	void connectionResetRetriesWithGrowingPause() {
		stubFor(post(urlEqualTo("/api/rooms/7/confirm-availability"))
			.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		assertThrows(HotelClientException.class, () -> this.hotelClient.confirm(7, command()));

		assertGrowingPauses(findAll(postRequestedFor(urlEqualTo("/api/rooms/7/confirm-availability"))));
	}

	@Test
	void timeoutRetriesWithGrowingPause() {
		stubFor(post(urlEqualTo("/api/rooms/7/confirm-availability"))
			.willReturn(aResponse().withStatus(200).withFixedDelay(1000)));

		assertThrows(HotelClientException.class, () -> this.hotelClient.confirm(7, command()));

		assertGrowingPauses(findAll(postRequestedFor(urlEqualTo("/api/rooms/7/confirm-availability"))));
	}

	@Test
	void serverErrorRetriesWithGrowingPause() {
		stubFor(post(urlEqualTo("/api/rooms/7/confirm-availability")).willReturn(aResponse().withStatus(500)));

		assertThrows(HotelClientException.class, () -> this.hotelClient.confirm(7, command()));

		assertGrowingPauses(findAll(postRequestedFor(urlEqualTo("/api/rooms/7/confirm-availability"))));
	}

	@Test
	void openHotelCallsStillReleases() {
		stubFor(post(urlEqualTo("/api/rooms/7/confirm-availability")).willReturn(aResponse().withStatus(500)));
		assertThrows(HotelClientException.class, () -> this.hotelClient.confirm(7, command()));
		assertEquals(CircuitBreaker.State.OPEN, this.circuitBreakerRegistry.circuitBreaker("hotelCalls").getState());
		stubFor(post(urlEqualTo("/api/rooms/7/release")).willReturn(aResponse().withStatus(200)));

		this.hotelClient.release(7, new ReleaseRoomCommand("wire-req", 9));

		assertEquals(1, findAll(postRequestedFor(urlEqualTo("/api/rooms/7/release"))).size());
		assertNotEquals(CircuitBreaker.State.OPEN,
				this.circuitBreakerRegistry.circuitBreaker("hotelRelease").getState());
	}

	private static ConfirmAvailabilityCommand command() {
		return new ConfirmAvailabilityCommand("wire-req", 9, LocalDate.parse("2026-11-20"),
				LocalDate.parse("2026-11-22"));
	}

	private static void assertGrowingPauses(List<com.github.tomakehurst.wiremock.verification.LoggedRequest> requests) {
		assertEquals(4, requests.size());
		long first = requests.get(1).getLoggedDate().getTime() - requests.get(0).getLoggedDate().getTime();
		long second = requests.get(2).getLoggedDate().getTime() - requests.get(1).getLoggedDate().getTime();
		long third = requests.get(3).getLoggedDate().getTime() - requests.get(2).getLoggedDate().getTime();
		assertTrue(second > first, "second=" + second + " first=" + first);
		assertTrue(third > second, "third=" + third + " second=" + second);
	}

}
