package com.hotelbooking.booking.reservation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.booking.account.dto.RegisterRequest;
import com.hotelbooking.booking.account.persistence.UserRepository;
import com.hotelbooking.booking.hotel.HotelClient;
import com.hotelbooking.booking.hotel.HotelConfirmStatus;
import com.hotelbooking.booking.reservation.dto.CreateBookingRequest;
import com.hotelbooking.booking.reservation.persistence.BookingEntity;
import com.hotelbooking.booking.reservation.persistence.BookingRepository;
import com.hotelbooking.booking.reservation.persistence.BookingStatus;

import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class BookingAccessTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean
	private HotelClient hotelClient;

	@Test
	void foreignBookingIsHiddenAndForbidden() throws Exception {
		when(this.hotelClient.confirm(anyLong(), any())).thenReturn(HotelConfirmStatus.OK);
		String tokenA = register(unique("access-a"));
		String tokenB = register(unique("access-b"));
		MvcResult created = postBooking(tokenA, new CreateBookingRequest(unique("req-access"), false, 3L,
				LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-22")));
		long id = json(created).get("id").asLong();

		MvcResult foreignGet = this.mockMvc.perform(get("/booking/" + id).header("Authorization", "Bearer " + tokenB))
			.andReturn();
		MvcResult foreignDelete = this.mockMvc
			.perform(delete("/booking/" + id).header("Authorization", "Bearer " + tokenB))
			.andReturn();
		JsonNode historyB = json(
				this.mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + tokenB)).andReturn());
		JsonNode historyA = json(
				this.mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + tokenA)).andReturn());

		assertError(foreignGet, 403, "Forbidden", "Недостаточно прав");
		assertError(foreignDelete, 403, "Forbidden", "Недостаточно прав");
		assertFalse(containsId(historyB, id));
		assertTrue(containsId(historyA, id));
		verify(this.hotelClient, never()).release(anyLong(), any());
	}

	@Test
	void missingBookingReturns404() throws Exception {
		String token = register(unique("access-missing"));

		MvcResult result = this.mockMvc.perform(get("/booking/999999999").header("Authorization", "Bearer " + token))
			.andReturn();

		JsonNode body = json(result);
		assertEquals(404, result.getResponse().getStatus());
		assertEquals("Not Found", body.get("error").asText());
		assertEquals("/booking/999999999", body.get("path").asText());
		assertEquals("Бронирование не найдено", body.get("message").asText());
	}

	@Test
	void historyPagination() throws Exception {
		String token = register(unique("access-page"));
		long userId = userId(token);
		long first = insert(userId, Instant.parse("2026-10-01T00:00:00Z"), unique("page-1"));
		long second = insert(userId, Instant.parse("2026-10-03T00:00:00Z"), unique("page-2"));
		long third = insert(userId, Instant.parse("2026-10-03T00:00:00Z"), unique("page-3"));

		MvcResult listed = this.mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + token))
			.andReturn();
		assertEquals(200, listed.getResponse().getStatus());
		JsonNode page = json(listed);
		assertEquals(0, page.get("page").asInt());
		assertEquals(20, page.get("size").asInt());
		assertEquals(3, page.get("totalElements").asLong());
		assertEquals(Set.of("content", "page", "size", "totalElements"), fieldNames(page));
		assertEquals(third, page.get("content").get(0).get("id").asLong());
		assertEquals(second, page.get("content").get(1).get("id").asLong());
		assertEquals(first, page.get("content").get(2).get("id").asLong());

		MvcResult size100 = this.mockMvc
			.perform(get("/bookings").param("size", "100").header("Authorization", "Bearer " + token))
			.andReturn();
		assertEquals(200, size100.getResponse().getStatus());
		assertEquals(100, json(size100).get("size").asInt());

		assertBadPage(token, "size", "101");
		assertBadPage(token, "page", "-1");
		assertBadPage(token, "size", "0");
		assertBadPage(token, "page", "abc");

		MvcResult beyondResult = this.mockMvc
			.perform(get("/bookings").param("page", "5").header("Authorization", "Bearer " + token))
			.andReturn();
		assertEquals(200, beyondResult.getResponse().getStatus());
		JsonNode beyond = json(beyondResult);
		assertEquals(0, beyond.get("content").size());
		assertEquals(3, beyond.get("totalElements").asLong());
		assertEquals(5, beyond.get("page").asInt());
	}

	private void assertBadPage(String token, String name, String value) throws Exception {
		MvcResult result = this.mockMvc
			.perform(get("/bookings").param(name, value).header("Authorization", "Bearer " + token))
			.andReturn();
		assertError(result, 400, "Bad Request", "Некорректный запрос");
	}

	private long insert(long userId, Instant createdAt, String requestId) {
		return new TransactionTemplate(this.transactionManager).execute(tx -> {
			BookingEntity entity = new BookingEntity();
			entity.setUser(this.userRepository.findById(userId).orElseThrow());
			entity.setRoomId(4L);
			entity.setStartDate(LocalDate.parse("2026-11-20"));
			entity.setEndDate(LocalDate.parse("2026-11-22"));
			entity.setStatus(BookingStatus.CONFIRMED);
			entity.setRequestId(requestId);
			entity.setCreatedAt(createdAt);
			return this.bookingRepository.saveAndFlush(entity).getId();
		});
	}

	private String register(String username) throws Exception {
		MvcResult result = this.mockMvc
			.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsString(new RegisterRequest(username, "password-1"))))
			.andReturn();
		assertEquals(201, result.getResponse().getStatus());
		return json(result).get("token").asText();
	}

	private MvcResult postBooking(String token, CreateBookingRequest request) throws Exception {
		return this.mockMvc
			.perform(post("/booking").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsString(request)))
			.andReturn();
	}

	private JsonNode json(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private void assertError(MvcResult result, int status, String error, String message) throws Exception {
		JsonNode body = json(result);
		assertEquals(status, result.getResponse().getStatus());
		assertEquals(status, body.get("status").asInt());
		assertEquals(error, body.get("error").asText());
		assertEquals(message, body.get("message").asText());
	}

	private static boolean containsId(JsonNode page, long id) {
		for (JsonNode item : page.get("content")) {
			if (item.get("id").asLong() == id) {
				return true;
			}
		}
		return false;
	}

	private static Set<String> fieldNames(JsonNode node) {
		Set<String> names = new HashSet<>();
		node.fieldNames().forEachRemaining(names::add);
		return names;
	}

	private static long userId(String token) throws Exception {
		return Long.parseLong(SignedJWT.parse(token).getJWTClaimsSet().getSubject());
	}

	private static String unique(String prefix) {
		return prefix + "-" + UUID.randomUUID();
	}

}
