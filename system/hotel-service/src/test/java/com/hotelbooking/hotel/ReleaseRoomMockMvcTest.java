package com.hotelbooking.hotel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class ReleaseRoomMockMvcTest {

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
	void releaseSeedLockMarksReleasedAndKeepsCounter() throws Exception {
		ListAppender<ILoggingEvent> appender = attachAppender();
		try {
			int timesBooked = this.roomRepository.findById(3L).orElseThrow().getTimesBooked();
			String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
			MvcResult result = release(token, "trace-release-1", 3L, "release-seed-1", 1L);
			assertEmptyOk(result);
			assertThat(this.roomLockRepository.findByBookingId(1L).orElseThrow().getStatus())
				.isEqualTo(RoomLock.Status.RELEASED);
			assertThat(this.roomRepository.findById(3L).orElseThrow().getTimesBooked()).isEqualTo(timesBooked);
			assertEvent(appender, "trace-release-1", "1", "release-seed-1");
		}
		finally {
			detachAppender(appender);
		}
	}

	@Test
	void repeatReleaseReturnsEmptyOkAndKeepsCounter() throws Exception {
		int timesBooked = this.roomRepository.findById(3L).orElseThrow().getTimesBooked();
		String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
		assertEmptyOk(release(token, "trace-release-repeat-1", 3L, "release-repeat", 1L));
		MvcResult second = release(token, "trace-release-repeat-2", 3L, "release-repeat", 1L);
		assertEmptyOk(second);
		assertThat(this.roomRepository.findById(3L).orElseThrow().getTimesBooked()).isEqualTo(timesBooked);
		assertThat(this.roomLockRepository.findByBookingId(1L).orElseThrow().getStatus())
			.isEqualTo(RoomLock.Status.RELEASED);
	}

	@Test
	void releaseWithoutActiveLockReturnsEmptyOk() throws Exception {
		int timesBooked = this.roomRepository.findById(3L).orElseThrow().getTimesBooked();
		String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
		MvcResult result = release(token, "trace-release-missing", 3L, "release-missing", 99L);
		assertEmptyOk(result);
		assertThat(this.roomRepository.findById(3L).orElseThrow().getTimesBooked()).isEqualTo(timesBooked);
		assertThat(this.roomLockRepository.findByBookingId(1L).orElseThrow().getStatus())
			.isEqualTo(RoomLock.Status.ACTIVE);
	}

	private MvcResult release(String token, String traceId, long roomId, String requestId, long bookingId)
			throws Exception {
		return this.mockMvc
			.perform(post("/api/rooms/" + roomId + "/release").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header(TraceConstants.HEADER, traceId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"requestId\":\"" + requestId + "\",\"bookingId\":" + bookingId + "}"))
			.andReturn();
	}

	private static void assertEmptyOk(MvcResult result) throws Exception {
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentAsString()).isEmpty();
	}

	private static void assertEvent(ListAppender<ILoggingEvent> appender, String traceId, String bookingId,
			String requestId) {
		ILoggingEvent loggingEvent = appender.list.stream()
			.filter(item -> "hotel.release".equals(item.getMDCPropertyMap().get(TraceConstants.MDC_EVENT)))
			.findFirst()
			.orElseThrow();
		assertThat(loggingEvent.getMDCPropertyMap()).containsEntry(TraceConstants.MDC_TRACE_ID, traceId)
			.containsEntry(TraceConstants.MDC_BOOKING_ID, bookingId)
			.containsEntry(TraceConstants.MDC_REQUEST_ID, requestId)
			.containsEntry(TraceConstants.MDC_EVENT, "hotel.release");
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
