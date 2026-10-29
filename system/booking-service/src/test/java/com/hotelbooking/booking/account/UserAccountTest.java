package com.hotelbooking.booking.account;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.booking.account.dto.AdminCreateUserRequest;
import com.hotelbooking.booking.account.dto.AuthRequest;
import com.hotelbooking.booking.account.dto.RegisterRequest;
import com.hotelbooking.booking.account.persistence.UserRepository;
import com.hotelbooking.booking.account.persistence.UserRole;
import com.hotelbooking.booking.hotel.HotelClient;
import com.hotelbooking.booking.reservation.persistence.BookingEntity;
import com.hotelbooking.booking.reservation.persistence.BookingRepository;
import com.hotelbooking.booking.reservation.persistence.BookingStatus;
import com.hotelbooking.booking.support.JwtFixtures;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class UserAccountTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean
	private HotelClient hotelClient;

	@Test
	void registerReturns201WithoutTokenType() throws Exception {
		String username = unique("reg");
		String password = "password-1";

		MvcResult result = this.mockMvc
			.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsString(new RegisterRequest(username, password))))
			.andReturn();

		JsonNode body = json(result);
		assertEquals(201, result.getResponse().getStatus());
		assertTrue(body.get("token").isTextual());
		assertEquals(3600, body.get("expiresIn").asLong());
		assertNull(body.get("tokenType"));
		SignedJWT jwt = SignedJWT.parse(body.get("token").asText());
		assertTrue(jwt.verify(new MACVerifier(JwtFixtures.SECRET.getBytes(StandardCharsets.UTF_8))));
		JWTClaimsSet claims = jwt.getJWTClaimsSet();
		assertEquals(Set.of("sub", "role", "iat", "exp"), claims.getClaims().keySet());
		assertEquals("USER", claims.getStringClaim("role"));
		assertTrue(claims.getSubject().matches("[0-9]+"));
		assertEquals(3600,
				ChronoUnit.SECONDS.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()));
		var user = this.userRepository.findByUsername(username).orElseThrow();
		assertEquals(UserRole.USER, user.getRole());
		assertNotEquals(password, user.getPassword());
		assertTrue(new BCryptPasswordEncoder(10).matches(password, user.getPassword()));
	}

	@Test
	void duplicateUsernameReturns409() throws Exception {
		String username = unique("dup");
		RegisterRequest request = new RegisterRequest(username, "password-1");
		assertEquals(201, register(request).getResponse().getStatus());

		MvcResult second = register(request);

		JsonNode body = json(second);
		assertEquals(409, second.getResponse().getStatus());
		assertEquals("Conflict", body.get("error").asText());
		assertEquals("Пользователь уже существует", body.get("message").asText());
		assertNull(body.get("token"));
		assertEquals(1,
				this.userRepository.findAll().stream().filter(user -> username.equals(user.getUsername())).count());
	}

	@Test
	void authReturns200() throws Exception {
		String username = unique("auth");
		register(new RegisterRequest(username, "password-1"));

		MvcResult result = this.mockMvc
			.perform(post("/user/auth").contentType(MediaType.APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsString(new AuthRequest(username, "password-1"))))
			.andReturn();

		JsonNode body = json(result);
		assertEquals(200, result.getResponse().getStatus());
		assertTrue(body.get("token").isTextual());
		assertEquals(3600, body.get("expiresIn").asLong());
		assertNull(body.get("tokenType"));
	}

	@Test
	void badCredentialsShareMessage() throws Exception {
		String username = unique("bad-cred");
		register(new RegisterRequest(username, "password-1"));

		MvcResult unknown = auth(unique("missing"), "password-1");
		MvcResult wrongPassword = auth(username, "other-password");

		JsonNode first = json(unknown);
		JsonNode second = json(wrongPassword);
		assertEquals(401, unknown.getResponse().getStatus());
		assertEquals(401, wrongPassword.getResponse().getStatus());
		assertEquals("Unauthorized", first.get("error").asText());
		assertEquals("Unauthorized", second.get("error").asText());
		assertEquals("Неверные учетные данные", first.get("message").asText());
		assertEquals(first.get("message").asText(), second.get("message").asText());
	}

	@Test
	void adminCreateUserOmitsPassword() throws Exception {
		String token = authToken("admin", "admin");
		String username = unique("created");

		MvcResult result = this.mockMvc
			.perform(post("/user").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.objectMapper
					.writeValueAsString(new AdminCreateUserRequest(username, "password-1", "USER"))))
			.andReturn();

		JsonNode body = json(result);
		assertEquals(201, result.getResponse().getStatus());
		assertTrue(body.get("id").isNumber());
		assertEquals(username, body.get("username").asText());
		assertEquals("USER", body.get("role").asText());
		assertNull(body.get("password"));
	}

	@Test
	void patchPasswordFromBodyAuthenticates() throws Exception {
		String username = unique("patch");
		JsonNode registered = json(register(new RegisterRequest(username, "password-1")));
		long id = Long.parseLong(SignedJWT.parse(registered.get("token").asText()).getJWTClaimsSet().getSubject());
		String admin = authToken("admin", "admin");

		MvcResult patched = this.mockMvc
			.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/user")
				.header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":" + id + ",\"password\":\"new-password-1\"}"))
			.andReturn();
		assertEquals(200, patched.getResponse().getStatus());

		assertEquals(401, auth(username, "password-1").getResponse().getStatus());
		assertEquals(200, auth(username, "new-password-1").getResponse().getStatus());
	}

	@Test
	void deleteUserWithCancelledBookingReturns409() throws Exception {
		String username = unique("del-busy");
		JsonNode registered = json(register(new RegisterRequest(username, "password-1")));
		long id = Long.parseLong(SignedJWT.parse(registered.get("token").asText()).getJWTClaimsSet().getSubject());
		new TransactionTemplate(this.transactionManager).executeWithoutResult(tx -> {
			BookingEntity booking = new BookingEntity();
			booking.setUser(this.userRepository.findById(id).orElseThrow());
			booking.setRoomId(3L);
			booking.setStartDate(LocalDate.parse("2026-11-01"));
			booking.setEndDate(LocalDate.parse("2026-11-02"));
			booking.setStatus(BookingStatus.CANCELLED);
			booking.setRequestId(unique("req-del-user"));
			booking.setCreatedAt(Instant.parse("2026-10-08T00:00:00Z"));
			this.bookingRepository.saveAndFlush(booking);
		});
		String admin = authToken("admin", "admin");

		MvcResult result = this.mockMvc
			.perform(delete("/user").param("id", Long.toString(id)).header("Authorization", "Bearer " + admin))
			.andReturn();

		JsonNode body = json(result);
		assertEquals(409, result.getResponse().getStatus());
		assertEquals("У пользователя есть бронирования", body.get("message").asText());
		assertTrue(this.userRepository.findById(id).isPresent());
	}

	@Test
	void deleteUserWithoutBookingsReturns204() throws Exception {
		String username = unique("del-free");
		JsonNode registered = json(register(new RegisterRequest(username, "password-1")));
		long id = Long.parseLong(SignedJWT.parse(registered.get("token").asText()).getJWTClaimsSet().getSubject());
		String admin = authToken("admin", "admin");

		MvcResult result = this.mockMvc
			.perform(delete("/user").param("id", Long.toString(id)).header("Authorization", "Bearer " + admin))
			.andReturn();

		assertEquals(204, result.getResponse().getStatus());
		assertEquals("", result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertTrue(this.userRepository.findById(id).isEmpty());
	}

	@Test
	void deletedUserTokenReturns401() throws Exception {
		String username = unique("del-token");
		JsonNode registered = json(register(new RegisterRequest(username, "password-1")));
		long id = Long.parseLong(SignedJWT.parse(registered.get("token").asText()).getJWTClaimsSet().getSubject());
		String admin = authToken("admin", "admin");
		MvcResult promoted = this.mockMvc
			.perform(patch("/user").header("Authorization", "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":" + id + ",\"role\":\"ADMIN\"}"))
			.andReturn();
		assertEquals(200, promoted.getResponse().getStatus());
		String token = authToken(username, "password-1");

		MvcResult deleted = this.mockMvc
			.perform(delete("/user").param("id", Long.toString(id)).header("Authorization", "Bearer " + admin))
			.andReturn();
		assertEquals(204, deleted.getResponse().getStatus());

		MvcResult result = this.mockMvc.perform(get("/user").header("Authorization", "Bearer " + token)).andReturn();
		JsonNode body = json(result);
		assertEquals(401, result.getResponse().getStatus());
		assertEquals("Недействительный токен", body.get("message").asText());
	}

	private MvcResult register(RegisterRequest request) throws Exception {
		return this.mockMvc
			.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsString(request)))
			.andReturn();
	}

	private MvcResult auth(String username, String password) throws Exception {
		return this.mockMvc
			.perform(post("/user/auth").contentType(MediaType.APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsString(new AuthRequest(username, password))))
			.andReturn();
	}

	private String authToken(String username, String password) throws Exception {
		MvcResult result = auth(username, password);
		assertEquals(200, result.getResponse().getStatus());
		return json(result).get("token").asText();
	}

	private JsonNode json(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private static String unique(String prefix) {
		return prefix + "-" + UUID.randomUUID();
	}

}
