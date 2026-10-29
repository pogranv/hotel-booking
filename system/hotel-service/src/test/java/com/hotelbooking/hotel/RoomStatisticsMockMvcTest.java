package com.hotelbooking.hotel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import com.hotelbooking.common.trace.TraceConstants;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoomStatisticsMockMvcTest {

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
	void userRoleReceivesForbidden() throws Exception {
		String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
		MvcResult result = this.mockMvc
			.perform(get("/api/rooms/statistics").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header(TraceConstants.HEADER, "trace-statistics-user"))
			.andReturn();
		assertError(result, 403, "Forbidden", "/api/rooms/statistics", "trace-statistics-user");
	}

	@Test
	void adminReceivesAllRoomsInCounterOrder() throws Exception {
		String token = sign("ADMIN", this.issuedAt, this.expiresAt, SECRET);
		this.mockMvc.perform(get("/api/rooms/statistics").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(5)))
			.andExpect(jsonPath("$[0].available").doesNotExist())
			.andExpect(jsonPath("$[0].id").value(1))
			.andExpect(jsonPath("$[0].hotelId").value(1))
			.andExpect(jsonPath("$[0].number").value("101"))
			.andExpect(jsonPath("$[0].timesBooked").value(0))
			.andExpect(jsonPath("$[1].id").value(2))
			.andExpect(jsonPath("$[1].hotelId").value(1))
			.andExpect(jsonPath("$[1].number").value("102"))
			.andExpect(jsonPath("$[1].timesBooked").value(0))
			.andExpect(jsonPath("$[2].id").value(4))
			.andExpect(jsonPath("$[2].hotelId").value(1))
			.andExpect(jsonPath("$[2].number").value("104"))
			.andExpect(jsonPath("$[2].timesBooked").value(0))
			.andExpect(jsonPath("$[3].id").value(3))
			.andExpect(jsonPath("$[3].hotelId").value(1))
			.andExpect(jsonPath("$[3].number").value("103"))
			.andExpect(jsonPath("$[3].timesBooked").value(1))
			.andExpect(jsonPath("$[4].id").value(5))
			.andExpect(jsonPath("$[4].hotelId").value(2))
			.andExpect(jsonPath("$[4].number").value("201"))
			.andExpect(jsonPath("$[4].timesBooked").value(1))
			.andExpect(jsonPath("$[*].id", contains(1, 2, 4, 3, 5)));
	}

	@Test
	void adminHotelFilterKeepsRelativeOrder() throws Exception {
		String token = sign("ADMIN", this.issuedAt, this.expiresAt, SECRET);
		this.mockMvc
			.perform(get("/api/rooms/statistics").param("hotelId", "1")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id", contains(1, 2, 4, 3)))
			.andExpect(jsonPath("$[*].number", contains("101", "102", "104", "103")))
			.andExpect(jsonPath("$[?(@.hotelId == 2)]").doesNotExist());
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
