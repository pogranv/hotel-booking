package com.hotelbooking.booking.security;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.booking.account.dto.RegisterRequest;
import com.hotelbooking.booking.hotel.HotelClient;
import com.hotelbooking.booking.support.JwtFixtures;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class BookingSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private HotelClient hotelClient;

	@Test
	void noAuthorizationReturns401() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/bookings").header("X-Trace-Id", "trace-401")).andReturn();

		JsonNode body = json(result);
		assertEquals(401, result.getResponse().getStatus());
		assertEquals("Unauthorized", body.get("error").asText());
		assertEquals("Недействительный токен", body.get("message").asText());
		assertEquals("trace-401", body.get("traceId").asText());
		assertEquals("/bookings", body.get("path").asText());
	}

	@Test
	void badSignatureReturns401() throws Exception {
		MvcResult result = this.mockMvc
			.perform(get("/bookings").header("Authorization", "Bearer " + JwtFixtures.foreignSignedToken(1, "USER")))
			.andReturn();

		assertError(result, 401);
	}

	@Test
	void expiredTokenReturns401() throws Exception {
		MvcResult result = this.mockMvc
			.perform(get("/bookings").header("Authorization", "Bearer " + JwtFixtures.expiredToken(1, "USER")))
			.andReturn();

		assertError(result, 401);
	}

	@Test
	void userRoleOnAdminPathsReturns403() throws Exception {
		String token = register(unique("sec-user"));
		String bearer = "Bearer " + token;

		MvcResult create = this.mockMvc
			.perform(post("/user").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + unique("should-not")
						+ "\",\"password\":\"password-1\",\"role\":\"USER\"}"))
			.andReturn();
		MvcResult list = this.mockMvc.perform(get("/user").header("Authorization", bearer)).andReturn();
		MvcResult update = this.mockMvc
			.perform(patch("/user").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":1,\"password\":\"password-2\"}"))
			.andReturn();
		MvcResult delete = this.mockMvc.perform(delete("/user").param("id", "1").header("Authorization", bearer))
			.andReturn();

		assertError(create, 403);
		assertError(list, 403);
		assertError(update, 403);
		assertError(delete, 403);
	}

	@Test
	void adminRoleOnBookingPathsReturns403() throws Exception {
		String token = auth("admin", "admin");
		String bearer = "Bearer " + token;
		String booking = "{\"requestId\":\"" + unique("admin-booking")
				+ "\",\"autoSelect\":false,\"roomId\":3,\"startDate\":\"2026-11-20\",\"endDate\":\"2026-11-22\"}";

		MvcResult create = this.mockMvc
			.perform(post("/booking").header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(booking))
			.andReturn();
		MvcResult list = this.mockMvc.perform(get("/bookings").header("Authorization", bearer)).andReturn();
		MvcResult existing = this.mockMvc.perform(get("/booking/1").header("Authorization", bearer)).andReturn();
		MvcResult missing = this.mockMvc.perform(get("/booking/999999999").header("Authorization", bearer)).andReturn();
		MvcResult cancel = this.mockMvc.perform(delete("/booking/1").header("Authorization", bearer)).andReturn();

		assertError(create, 403);
		assertError(list, 403);
		assertError(existing, 403);
		assertError(missing, 403);
		assertError(cancel, 403);
	}

	private String register(String username) throws Exception {
		MvcResult result = this.mockMvc
			.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsString(new RegisterRequest(username, "password-1"))))
			.andReturn();
		assertEquals(201, result.getResponse().getStatus());
		return json(result).get("token").asText();
	}

	private String auth(String username, String password) throws Exception {
		MvcResult result = this.mockMvc
			.perform(post("/user/auth").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
			.andReturn();
		assertEquals(200, result.getResponse().getStatus());
		return json(result).get("token").asText();
	}

	private void assertError(MvcResult result, int status) throws Exception {
		JsonNode body = json(result);
		assertEquals(status, result.getResponse().getStatus());
		assertEquals(status, body.get("status").asInt());
		assertTrueMessage(body);
	}

	private static void assertTrueMessage(JsonNode body) {
		assertEquals(true, body.hasNonNull("error"));
		assertEquals(true, body.hasNonNull("message"));
	}

	private JsonNode json(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private static String unique(String prefix) {
		return prefix + "-" + UUID.randomUUID();
	}

}
