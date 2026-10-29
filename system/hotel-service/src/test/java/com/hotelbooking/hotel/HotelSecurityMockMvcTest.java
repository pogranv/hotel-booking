package com.hotelbooking.hotel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HotelSecurityMockMvcTest {

	private static final String SECRET = "0123456789abcdef0123456789abcdef";

	private static final String FOREIGN_SECRET = "fedcba9876543210fedcba9876543210";

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
	void missingAuthorizationReturnsUnauthorized() throws Exception {
		MvcResult hotels = this.mockMvc.perform(get("/api/hotels")).andReturn();
		assertError(hotels, 401, "Unauthorized", "/api/hotels", null);
		MvcResult confirm = this.mockMvc.perform(post("/api/rooms/1/confirm-availability")
			.contentType(MediaType.APPLICATION_JSON)
			.content(
					"{\"requestId\":\"security-missing\",\"bookingId\":40,\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-03\"}"))
			.andReturn();
		assertError(confirm, 401, "Unauthorized", "/api/rooms/1/confirm-availability", null);
	}

	@Test
	void invalidSignatureReturnsUnauthorized() throws Exception {
		String token = sign("USER", this.issuedAt, this.expiresAt, FOREIGN_SECRET);
		MvcResult result = this.mockMvc.perform(get("/api/hotels").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andReturn();
		assertError(result, 401, "Unauthorized", "/api/hotels", null);
	}

	@Test
	void expiredTokenReturnsUnauthorized() throws Exception {
		Instant expiredAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
		Instant issued = expiredAt.minus(1, ChronoUnit.HOURS);
		String token = sign("USER", issued, expiredAt, SECRET);
		MvcResult result = this.mockMvc.perform(get("/api/hotels").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andReturn();
		assertError(result, 401, "Unauthorized", "/api/hotels", null);
	}

	@Test
	void userRoleOnAdminOperationsReturnsForbidden() throws Exception {
		String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
		MvcResult createHotel = this.mockMvc
			.perform(post("/api/hotels").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Hotel C\",\"address\":\"Address C\"}"))
			.andReturn();
		assertError(createHotel, 403, "Forbidden", "/api/hotels", null);
		MvcResult deleteRoom = this.mockMvc
			.perform(delete("/api/rooms/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andReturn();
		assertError(deleteRoom, 403, "Forbidden", "/api/rooms/1", null);
		MvcResult statistics = this.mockMvc
			.perform(get("/api/rooms/statistics").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andReturn();
		assertError(statistics, 403, "Forbidden", "/api/rooms/statistics", null);
	}

	@Test
	void userRoleOnConfirmAndReleaseIsNotForbidden() throws Exception {
		String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
		MvcResult confirm = this.mockMvc.perform(post("/api/rooms/1/confirm-availability")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(
					"{\"requestId\":\"security-confirm\",\"bookingId\":41,\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-03\"}"))
			.andReturn();
		assertThat(confirm.getResponse().getStatus()).isEqualTo(200);
		assertThat(confirm.getResponse().getContentAsString()).isEmpty();
		MvcResult release = this.mockMvc
			.perform(post("/api/rooms/3/release").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"requestId\":\"security-release\",\"bookingId\":1}"))
			.andReturn();
		assertThat(release.getResponse().getStatus()).isEqualTo(200);
		assertThat(release.getResponse().getContentAsString()).isEmpty();
	}

	@Test
	void adminRoleOnStatisticsIsNotForbidden() throws Exception {
		String token = sign("ADMIN", this.issuedAt, this.expiresAt, SECRET);
		this.mockMvc.perform(get("/api/rooms/statistics").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());
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
