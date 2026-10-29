package com.hotelbooking.booking.seed;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.booking.account.dto.AdminCreateUserRequest;
import com.hotelbooking.booking.account.persistence.UserRepository;
import com.hotelbooking.booking.account.persistence.UserRole;
import com.hotelbooking.booking.hotel.HotelClient;
import com.hotelbooking.booking.reservation.persistence.BookingEntity;
import com.hotelbooking.booking.reservation.persistence.BookingRepository;
import com.hotelbooking.booking.reservation.persistence.BookingStatus;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class DemoDataSeederTest {

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
	void seedUsersBookingsAndLogins() throws Exception {
		assertEquals("admin", this.userRepository.findById(1L).orElseThrow().getUsername());
		assertEquals(UserRole.ADMIN, this.userRepository.findById(1L).orElseThrow().getRole());
		assertEquals("user1", this.userRepository.findById(2L).orElseThrow().getUsername());
		assertEquals(UserRole.USER, this.userRepository.findById(2L).orElseThrow().getRole());
		assertEquals("user2", this.userRepository.findById(3L).orElseThrow().getUsername());
		assertEquals(UserRole.USER, this.userRepository.findById(3L).orElseThrow().getRole());
		new TransactionTemplate(this.transactionManager).executeWithoutResult(tx -> {
			assertBooking(1L, 2L, 3L, "2026-11-10", "2026-11-12", BookingStatus.CONFIRMED, "seed-booking-1");
			assertBooking(2L, 3L, 5L, "2026-12-01", "2026-12-03", BookingStatus.CONFIRMED, "seed-booking-2");
			assertBooking(3L, 2L, 2L, "2026-11-01", "2026-11-02", BookingStatus.CANCELLED, "seed-booking-3");
		});

		assertEquals(200, auth("user1", "user1").getResponse().getStatus());
		MvcResult admin = auth("admin", "admin");
		assertEquals(200, admin.getResponse().getStatus());
		String token = this.objectMapper.readTree(admin.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.get("token")
			.asText();
		MvcResult created = this.mockMvc
			.perform(post("/user").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsString(
						new AdminCreateUserRequest("seed-extra-" + System.nanoTime(), "password-1", "USER"))))
			.andReturn();
		JsonNode body = this.objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertEquals(201, created.getResponse().getStatus());
		assertTrue(body.get("id").asLong() > 3);
	}

	private void assertBooking(long id, long userId, long roomId, String start, String end, BookingStatus status,
			String requestId) {
		BookingEntity booking = this.bookingRepository.findById(id).orElseThrow();
		assertEquals(userId, booking.getUser().getId().longValue());
		assertEquals(roomId, booking.getRoomId().longValue());
		assertEquals(LocalDate.parse(start), booking.getStartDate());
		assertEquals(LocalDate.parse(end), booking.getEndDate());
		assertEquals(status, booking.getStatus());
		assertEquals(requestId, booking.getRequestId());
	}

	private MvcResult auth(String username, String password) throws Exception {
		return this.mockMvc
			.perform(post("/user/auth").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
			.andReturn();
	}

}
