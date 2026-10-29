package com.hotelbooking.hotel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hotelbooking.common.trace.TraceConstants;
import com.hotelbooking.hotel.availability.RoomAvailabilityServiceImpl;
import com.hotelbooking.hotel.availability.RoomLock;
import com.hotelbooking.hotel.availability.RoomLockRepository;
import com.hotelbooking.hotel.hotel.HotelRepository;
import com.hotelbooking.hotel.room.RoomRepository;
import com.hotelbooking.hotel.seed.HotelSeedLoader;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class ConfirmAvailabilityMockMvcTest {

	private static final String SECRET = "0123456789abcdef0123456789abcdef";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RoomLockRepository roomLockRepository;

	@Autowired
	private RoomRepository roomRepository;

	@Autowired
	private HotelRepository hotelRepository;

	@Autowired
	private HotelSeedLoader hotelSeedLoader;

	private Instant issuedAt;

	private Instant expiresAt;

	@BeforeEach
	void setUp() {
		this.roomLockRepository.deleteAll();
		this.roomRepository.deleteAll();
		this.hotelRepository.deleteAll();
		this.hotelSeedLoader.seed();
		this.issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		this.expiresAt = this.issuedAt.plus(1, ChronoUnit.HOURS);
	}

	@Test
	void confirmNewLockIncrementsCounterOnce() throws Exception {
		ListAppender<ILoggingEvent> appender = attachAppender();
		try {
			String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
			MvcResult result = confirm(token, "trace-confirm-1", 1L, "req-new-1", 10L, "2026-11-01", "2026-11-03");
			assertEmptyOk(result);
			assertThat(this.roomRepository.findById(1L).orElseThrow().getTimesBooked()).isEqualTo(1);
			RoomLock lock = this.roomLockRepository.findByRequestId("req-new-1").orElseThrow();
			assertThat(lock.getStatus()).isEqualTo(RoomLock.Status.ACTIVE);
			assertEvent(appender, "hotel.lock", "trace-confirm-1", "10", "req-new-1");
		}
		finally {
			detachAppender(appender);
		}
	}

	@Test
	void repeatRequestIdDoesNotIncrementCounter() throws Exception {
		String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
		assertEmptyOk(confirm(token, "trace-repeat-1", 1L, "req-repeat-1", 11L, "2026-11-01", "2026-11-03"));
		MvcResult second = confirm(token, "trace-repeat-2", 1L, "req-repeat-1", 11L, "2026-11-01", "2026-11-03");
		assertEmptyOk(second);
		assertThat(this.roomRepository.findById(1L).orElseThrow().getTimesBooked()).isEqualTo(1);
		assertThat(this.roomLockRepository.findByRequestId("req-repeat-1")).isPresent();
		assertThat(
				this.roomLockRepository.findAll().stream().filter(lock -> "req-repeat-1".equals(lock.getRequestId())))
			.hasSize(1);
	}

	@Test
	void repeatReleasedRequestIdReturnsConflict() throws Exception {
		ListAppender<ILoggingEvent> appender = attachAppender();
		try {
			String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
			assertEmptyOk(confirm(token, "trace-released-1", 1L, "req-released-1", 12L, "2026-11-01", "2026-11-03"));
			RoomLock lock = this.roomLockRepository.findByRequestId("req-released-1").orElseThrow();
			lock.setStatus(RoomLock.Status.RELEASED);
			this.roomLockRepository.saveAndFlush(lock);
			MvcResult conflict = confirm(token, "trace-released-2", 1L, "req-released-1", 12L, "2026-11-01",
					"2026-11-03");
			assertError(conflict, 409, "Conflict", "/api/rooms/1/confirm-availability", "trace-released-2");
			assertThat(this.roomRepository.findById(1L).orElseThrow().getTimesBooked()).isEqualTo(1);
			assertThat(this.roomLockRepository.findAll()
				.stream()
				.filter(item -> "req-released-1".equals(item.getRequestId()))).hasSize(1);
			assertEvent(appender, "hotel.lock.conflict", "trace-released-2", "12", "req-released-1");
		}
		finally {
			detachAppender(appender);
		}
	}

	@Test
	void overlappingIntervalReturnsConflict() throws Exception {
		ListAppender<ILoggingEvent> appender = attachAppender();
		try {
			String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
			MvcResult conflict = confirm(token, "trace-overlap", 3L, "req-overlap", 13L, "2026-11-11", "2026-11-13");
			assertError(conflict, 409, "Conflict", "/api/rooms/3/confirm-availability", "trace-overlap");
			assertThat(this.roomRepository.findById(3L).orElseThrow().getTimesBooked()).isEqualTo(1);
			assertThat(this.roomLockRepository.findByRequestId("req-overlap")).isEmpty();
			assertEvent(appender, "hotel.lock.conflict", "trace-overlap", "13", "req-overlap");
		}
		finally {
			detachAppender(appender);
		}
	}

	@Test
	void departureBoundaryCreatesLock() throws Exception {
		ListAppender<ILoggingEvent> appender = attachAppender();
		try {
			String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
			MvcResult result = confirm(token, "trace-departure", 3L, "req-departure", 14L, "2026-11-12", "2026-11-14");
			assertEmptyOk(result);
			RoomLock lock = this.roomLockRepository.findByRequestId("req-departure").orElseThrow();
			assertThat(lock.getStatus()).isEqualTo(RoomLock.Status.ACTIVE);
			assertThat(this.roomRepository.findById(3L).orElseThrow().getTimesBooked()).isEqualTo(2);
			assertEvent(appender, "hotel.lock", "trace-departure", "14", "req-departure");
		}
		finally {
			detachAppender(appender);
		}
	}

	@Test
	void arrivalBoundaryCreatesLock() throws Exception {
		ListAppender<ILoggingEvent> appender = attachAppender();
		try {
			String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
			MvcResult result = confirm(token, "trace-arrival", 3L, "req-arrival", 15L, "2026-11-08", "2026-11-10");
			assertEmptyOk(result);
			RoomLock lock = this.roomLockRepository.findByRequestId("req-arrival").orElseThrow();
			assertThat(lock.getStatus()).isEqualTo(RoomLock.Status.ACTIVE);
			assertThat(this.roomRepository.findById(3L).orElseThrow().getTimesBooked()).isEqualTo(2);
			assertEvent(appender, "hotel.lock", "trace-arrival", "15", "req-arrival");
		}
		finally {
			detachAppender(appender);
		}
	}

	@Test
	void parallelOverlappingConfirmsYieldOneSuccessAndOneConflict() throws Exception {
		String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<MvcResult> first = executor.submit(() -> confirmAfter(token, ready, start, "parallel-a", 31L));
			Future<MvcResult> second = executor.submit(() -> confirmAfter(token, ready, start, "parallel-b", 32L));
			assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			MvcResult resultA = first.get(30, TimeUnit.SECONDS);
			MvcResult resultB = second.get(30, TimeUnit.SECONDS);
			List<Integer> statuses = List.of(resultA.getResponse().getStatus(), resultB.getResponse().getStatus());
			assertThat(statuses).containsExactlyInAnyOrder(200, 409);
			MvcResult ok = resultA.getResponse().getStatus() == 200 ? resultA : resultB;
			MvcResult conflict = resultA.getResponse().getStatus() == 409 ? resultA : resultB;
			assertThat(ok.getResponse().getContentAsString()).isEmpty();
			assertError(conflict, 409, "Conflict", "/api/rooms/1/confirm-availability", null);
			assertThat(this.roomRepository.findById(1L).orElseThrow().getTimesBooked()).isEqualTo(1);
			List<RoomLock> active = this.roomLockRepository.findByRoomId(1L)
				.stream()
				.filter(lock -> lock.getStatus() == RoomLock.Status.ACTIVE)
				.toList();
			assertThat(active).hasSize(1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	private MvcResult confirmAfter(String token, CountDownLatch ready, CountDownLatch start, String requestId,
			long bookingId) throws Exception {
		ready.countDown();
		assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
		return confirm(token, null, 1L, requestId, bookingId, "2026-11-01", "2026-11-05");
	}

	private MvcResult confirm(String token, String traceId, long roomId, String requestId, long bookingId,
			String startDate, String endDate) throws Exception {
		var request = post("/api/rooms/" + roomId + "/confirm-availability")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"requestId\":\"" + requestId + "\",\"bookingId\":" + bookingId + ",\"startDate\":\"" + startDate
					+ "\",\"endDate\":\"" + endDate + "\"}");
		if (traceId != null) {
			request.header(TraceConstants.HEADER, traceId);
		}
		return this.mockMvc.perform(request).andReturn();
	}

	private static void assertEmptyOk(MvcResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentAsString()).isEmpty();
	}

	private static void assertError(MvcResult result, int status, String error, String path, String traceId)
			throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
		assertThat(result.getResponse().getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
		jsonPath("$.timestamp").isNotEmpty().match(result);
		jsonPath("$.status").value(status).match(result);
		jsonPath("$.error").value(error).match(result);
		jsonPath("$.message").exists().match(result);
		jsonPath("$.path").value(path).match(result);
		if (traceId == null) {
			jsonPath("$.traceId").value(nullValue()).match(result);
		}
		else {
			jsonPath("$.traceId").value(traceId).match(result);
		}
	}

	private static void assertEvent(ListAppender<ILoggingEvent> appender, String event, String traceId,
			String bookingId, String requestId) {
		ILoggingEvent loggingEvent = appender.list.stream()
			.filter(item -> event.equals(item.getMDCPropertyMap().get(TraceConstants.MDC_EVENT)))
			.filter(item -> requestId.equals(item.getMDCPropertyMap().get(TraceConstants.MDC_REQUEST_ID)))
			.findFirst()
			.orElseThrow();
		assertThat(loggingEvent.getMDCPropertyMap()).containsEntry(TraceConstants.MDC_TRACE_ID, traceId)
			.containsEntry(TraceConstants.MDC_BOOKING_ID, bookingId)
			.containsEntry(TraceConstants.MDC_REQUEST_ID, requestId)
			.containsEntry(TraceConstants.MDC_EVENT, event);
	}

	private static ListAppender<ILoggingEvent> attachAppender() {
		Logger logger = (Logger) LoggerFactory.getLogger(RoomAvailabilityServiceImpl.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		return appender;
	}

	private static void detachAppender(ListAppender<ILoggingEvent> appender) {
		Logger logger = (Logger) LoggerFactory.getLogger(RoomAvailabilityServiceImpl.class);
		logger.detachAppender(appender);
		appender.stop();
	}

	private static String sign(String role, Instant issuedAt, Instant expiresAt, String secret) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("1")
			.claim("role", role)
			.issueTime(Date.from(issuedAt))
			.expirationTime(Date.from(expiresAt))
			.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
		return jwt.serialize();
	}

}
