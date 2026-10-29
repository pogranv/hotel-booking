package com.hotelbooking.hotel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import com.hotelbooking.hotel.hotel.HotelRepository;
import com.hotelbooking.hotel.availability.RoomLockRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecommendationOrderMockMvcTest {

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
	void recommendReturnsRoomsInSeedOrder() throws Exception {
		String token = sign("USER", this.issuedAt, this.expiresAt, SECRET);
		this.mockMvc.perform(get("/api/rooms/recommend").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].number", contains("101", "102", "103", "201")));
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
