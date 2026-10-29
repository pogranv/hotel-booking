package com.hotelbooking.booking.saga;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.booking.account.dto.RegisterRequest;
import com.hotelbooking.booking.account.persistence.UserRepository;
import com.hotelbooking.booking.hotel.HotelClient;
import com.hotelbooking.booking.hotel.HotelClientException;
import com.hotelbooking.booking.hotel.HotelConfirmStatus;
import com.hotelbooking.booking.hotel.dto.ConfirmAvailabilityCommand;
import com.hotelbooking.booking.hotel.dto.ReleaseRoomCommand;
import com.hotelbooking.booking.hotel.dto.RoomRecommendation;
import com.hotelbooking.booking.reservation.dto.CreateBookingRequest;
import com.hotelbooking.booking.reservation.persistence.BookingEntity;
import com.hotelbooking.booking.reservation.persistence.BookingRepository;
import com.hotelbooking.booking.reservation.persistence.BookingStatus;

import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class BookingSagaTest {

	private static final LocalDate START = LocalDate.parse("2026-11-20");

	private static final LocalDate END = LocalDate.parse("2026-11-22");

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
	void successfulConfirmReturns201Confirmed() throws Exception {
		String token = register(unique("saga-ok"));
		long userId = userId(token);
		String requestId = unique("req-ok");
		when(this.hotelClient.confirm(eq(3L), any())).thenAnswer(invocation -> {
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
			ConfirmAvailabilityCommand command = invocation.getArgument(1);
			BookingEntity row = this.bookingRepository.findByRequestId(command.requestId()).orElseThrow();
			assertEquals(BookingStatus.PENDING, row.getStatus());
			return HotelConfirmStatus.OK;
		});

		MvcResult result = postBooking(token, booking(requestId, false, 3L, START, END));

		JsonNode body = json(result);
		assertEquals(201, result.getResponse().getStatus());
		assertEquals("CONFIRMED", body.get("status").asText());
		assertEquals(3L, body.get("roomId").asLong());
		assertEquals(userId, body.get("userId").asLong());
		ArgumentCaptor<ConfirmAvailabilityCommand> captor = ArgumentCaptor.forClass(ConfirmAvailabilityCommand.class);
		verify(this.hotelClient).confirm(eq(3L), captor.capture());
		assertEquals(requestId, captor.getValue().requestId());
		assertEquals(body.get("id").asLong(), captor.getValue().bookingId());
		assertEquals(START, captor.getValue().startDate());
		assertEquals(END, captor.getValue().endDate());
		verify(this.hotelClient, never()).release(anyLong(), any());
		verify(this.hotelClient, never()).recommend();
	}

	@Test
	void hotelConflictReturns409CancelledBooking() throws Exception {
		String token = register(unique("saga-conflict"));
		when(this.hotelClient.confirm(anyLong(), any())).thenReturn(HotelConfirmStatus.CONFLICT);

		MvcResult result = postBooking(token, booking(unique("req-conflict"), false, 3L, START, END));

		JsonNode body = json(result);
		assertEquals(409, result.getResponse().getStatus());
		assertEquals("CANCELLED", body.get("status").asText());
		assertNull(body.get("error"));
		assertNull(body.get("timestamp"));
		verify(this.hotelClient, never()).release(anyLong(), any());
	}

	@Test
	void hotelNotFoundReturns404CancelledBooking() throws Exception {
		String token = register(unique("saga-missing-room"));
		when(this.hotelClient.confirm(anyLong(), any())).thenReturn(HotelConfirmStatus.NOT_FOUND);

		MvcResult result = postBooking(token, booking(unique("req-missing-room"), false, 3L, START, END));

		JsonNode body = json(result);
		assertEquals(404, result.getResponse().getStatus());
		assertEquals("CANCELLED", body.get("status").asText());
		verify(this.hotelClient, never()).release(anyLong(), any());
	}

	@Test
	void confirmFailureReleasesAndReturns503() throws Exception {
		String token = register(unique("saga-down"));
		String requestId = unique("req-down");
		when(this.hotelClient.confirm(anyLong(), any())).thenThrow(new HotelClientException("down"));

		MvcResult result = postBooking(token, booking(requestId, false, 3L, START, END));

		JsonNode body = json(result);
		assertEquals(503, result.getResponse().getStatus());
		assertEquals("CANCELLED", body.get("status").asText());
		ArgumentCaptor<ReleaseRoomCommand> captor = ArgumentCaptor.forClass(ReleaseRoomCommand.class);
		verify(this.hotelClient).release(eq(3L), captor.capture());
		assertEquals(requestId, captor.getValue().requestId());
		assertEquals(body.get("id").asLong(), captor.getValue().bookingId());
	}

	@Test
	void sameRequestIdReturns200WithoutSecondCall() throws Exception {
		String token = register(unique("saga-replay"));
		String requestId = unique("req-replay");
		when(this.hotelClient.confirm(anyLong(), any())).thenReturn(HotelConfirmStatus.OK);
		MvcResult first = postBooking(token, booking(requestId, false, 3L, START, END));
		long id = json(first).get("id").asLong();

		MvcResult second = postBooking(token,
				booking(requestId, false, 9L, LocalDate.parse("2027-01-01"), LocalDate.parse("2027-01-03")));

		JsonNode body = json(second);
		assertEquals(200, second.getResponse().getStatus());
		assertEquals(id, body.get("id").asLong());
		assertEquals(1, countByRequestId(requestId));
		verify(this.hotelClient, times(1)).confirm(anyLong(), any());
		verify(this.hotelClient, never()).release(anyLong(), any());
	}

	@Test
	void parallelSameRequestIdKeepsOneRow() throws Exception {
		String token = register(unique("saga-parallel"));
		String requestId = unique("req-parallel");
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch releaseConfirm = new CountDownLatch(1);
		AtomicInteger calls = new AtomicInteger();
		when(this.hotelClient.confirm(anyLong(), any())).thenAnswer(invocation -> {
			calls.incrementAndGet();
			entered.countDown();
			assertTrue(releaseConfirm.await(10, TimeUnit.SECONDS));
			return HotelConfirmStatus.OK;
		});
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Callable<MvcResult> call = () -> {
				ready.countDown();
				assertTrue(start.await(5, TimeUnit.SECONDS));
				return postBooking(token, booking(requestId, false, 3L, START, END));
			};
			Future<MvcResult> first = executor.submit(call);
			Future<MvcResult> second = executor.submit(call);
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			MvcResult early = awaitDone(first, second);
			JsonNode earlyBody = json(early);
			assertEquals(200, early.getResponse().getStatus());
			assertEquals("PENDING", earlyBody.get("status").asText());
			releaseConfirm.countDown();
			MvcResult left = first.get(10, TimeUnit.SECONDS);
			MvcResult right = second.get(10, TimeUnit.SECONDS);
			long leftId = json(left).get("id").asLong();
			long rightId = json(right).get("id").asLong();
			assertEquals(leftId, rightId);
			assertEquals(earlyBody.get("id").asLong(), leftId);
			assertTrue(left.getResponse().getStatus() == 200 || right.getResponse().getStatus() == 200);
			assertEquals(1, calls.get());
			assertEquals(1, countByRequestId(requestId));
		}
		finally {
			releaseConfirm.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void stringAutoSelectReturns400() throws Exception {
		String token = register(unique("saga-bool"));
		String requestId = unique("req-bool");
		String body = "{\"requestId\":\"" + requestId
				+ "\",\"autoSelect\":\"true\",\"startDate\":\"2027-01-10\",\"endDate\":\"2027-01-12\"}";

		MvcResult result = this.mockMvc
			.perform(post("/booking").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andReturn();

		assertError(result, 400, "Bad Request", "Некорректный запрос");
		assertEquals(0, countByRequestId(requestId));
		verifyNoInteractions(this.hotelClient);
	}

	@Test
	void autoSelectUsesOnlyFirstRoom() throws Exception {
		String token = register(unique("saga-auto"));
		when(this.hotelClient.recommend()).thenReturn(java.util.List.of(new RoomRecommendation(11, 1, "101", true, 1),
				new RoomRecommendation(22, 1, "102", true, 2)));
		when(this.hotelClient.confirm(eq(11L), any())).thenReturn(HotelConfirmStatus.CONFLICT);

		MvcResult result = postBooking(token, booking(unique("req-auto"), true, 99L, START, END));

		JsonNode body = json(result);
		assertEquals(409, result.getResponse().getStatus());
		assertEquals("CANCELLED", body.get("status").asText());
		verify(this.hotelClient).confirm(eq(11L), any());
		verify(this.hotelClient, never()).confirm(eq(22L), any());
	}

	@Test
	void emptyRecommendationReturns409Error() throws Exception {
		String token = register(unique("saga-empty"));
		String requestId = unique("req-empty");
		when(this.hotelClient.recommend()).thenReturn(java.util.List.of());

		MvcResult result = postBooking(token, booking(requestId, true, null, START, END));

		assertError(result, 409, "Conflict", "Свободных номеров нет");
		assertEquals(0, countByRequestId(requestId));
		verify(this.hotelClient, never()).confirm(anyLong(), any());
	}

	@Test
	void recommendationFailureReturns503Error() throws Exception {
		String token = register(unique("saga-rec-down"));
		String requestId = unique("req-rec-down");
		when(this.hotelClient.recommend()).thenThrow(new HotelClientException("down"));

		MvcResult result = postBooking(token, booking(requestId, true, null, START, END));

		assertError(result, 503, "Service Unavailable", "Сервис отелей недоступен");
		assertEquals(0, countByRequestId(requestId));
		verify(this.hotelClient, never()).confirm(anyLong(), any());
		verify(this.hotelClient, never()).release(anyLong(), any());
	}

	@Test
	void equalDatesReturn400() throws Exception {
		String token = register(unique("saga-equal"));
		String requestId = unique("req-equal");
		LocalDate day = LocalDate.parse("2026-11-20");

		MvcResult result = postBooking(token, booking(requestId, false, 3L, day, day));

		assertError(result, 400, "Bad Request", "Дата выезда должна быть позже даты заезда");
		assertEquals(0, countByRequestId(requestId));
		verifyNoInteractions(this.hotelClient);
	}

	@Test
	void reversedDatesReturn400() throws Exception {
		String token = register(unique("saga-reversed"));
		String requestId = unique("req-reversed");

		MvcResult result = postBooking(token,
				booking(requestId, false, 3L, LocalDate.parse("2026-11-22"), LocalDate.parse("2026-11-20")));

		assertError(result, 400, "Bad Request", "Дата выезда должна быть позже даты заезда");
		assertEquals(0, countByRequestId(requestId));
		verifyNoInteractions(this.hotelClient);
	}

	@Test
	void pastIntervalIsNotRejected() throws Exception {
		String token = register(unique("saga-past"));
		when(this.hotelClient.confirm(anyLong(), any())).thenReturn(HotelConfirmStatus.OK);

		MvcResult result = postBooking(token,
				booking(unique("req-past"), false, 3L, LocalDate.parse("2020-01-01"), LocalDate.parse("2020-01-03")));

		assertEquals(201, result.getResponse().getStatus());
		assertEquals("CONFIRMED", json(result).get("status").asText());
	}

	@Test
	void deleteCancelledReturnsAsIs() throws Exception {
		String token = register(unique("saga-del-cancel"));
		long userId = userId(token);
		String requestId = unique("req-del-cancel");
		long id = insertBooking(userId, BookingStatus.CANCELLED, requestId, Instant.parse("2026-10-01T00:00:00Z"));

		MvcResult result = deleteBooking(token, id);

		JsonNode body = json(result);
		assertEquals(200, result.getResponse().getStatus());
		assertEquals(id, body.get("id").asLong());
		assertEquals("CANCELLED", body.get("status").asText());
		verify(this.hotelClient, never()).release(anyLong(), any());
		verify(this.hotelClient, never()).confirm(anyLong(), any());
	}

	@Test
	void deleteConfirmedReleasesThenCancels() throws Exception {
		String token = register(unique("saga-del-ok"));
		String requestId = unique("req-del-ok");
		when(this.hotelClient.confirm(anyLong(), any())).thenReturn(HotelConfirmStatus.OK);
		doAnswer(invocation -> {
			ReleaseRoomCommand command = invocation.getArgument(1);
			BookingEntity row = this.bookingRepository.findById(command.bookingId()).orElseThrow();
			assertEquals(BookingStatus.CONFIRMED, row.getStatus());
			assertEquals(requestId, command.requestId());
			return null;
		}).when(this.hotelClient).release(eq(3L), any());
		MvcResult created = postBooking(token, booking(requestId, false, 3L, START, END));
		long id = json(created).get("id").asLong();

		MvcResult result = deleteBooking(token, id);

		assertEquals(200, result.getResponse().getStatus());
		assertEquals("CANCELLED", json(result).get("status").asText());
		verify(this.hotelClient).release(eq(3L), any());
	}

	@Test
	void deletePendingReleasesThenCancels() throws Exception {
		String token = register(unique("saga-del-pending"));
		long userId = userId(token);
		String requestId = unique("req-del-pending");
		long id = insertBooking(userId, BookingStatus.PENDING, requestId, Instant.parse("2026-10-04T00:00:00Z"));

		MvcResult result = deleteBooking(token, id);

		assertEquals(200, result.getResponse().getStatus());
		assertEquals("CANCELLED", json(result).get("status").asText());
		ArgumentCaptor<ReleaseRoomCommand> captor = ArgumentCaptor.forClass(ReleaseRoomCommand.class);
		verify(this.hotelClient).release(eq(3L), captor.capture());
		assertEquals(requestId, captor.getValue().requestId());
		assertEquals(id, captor.getValue().bookingId());
	}

	@Test
	void failedReleaseOnDeleteKeepsStatus() throws Exception {
		String token = register(unique("saga-rel-fail"));
		long userId = userId(token);
		long id = insertBooking(userId, BookingStatus.CONFIRMED, unique("req-rel-fail"),
				Instant.parse("2026-10-05T00:00:00Z"));
		doThrow(new HotelClientException("down")).when(this.hotelClient).release(anyLong(), any());

		MvcResult result = deleteBooking(token, id);

		JsonNode body = json(result);
		assertEquals(503, result.getResponse().getStatus());
		assertEquals("CONFIRMED", body.get("status").asText());
		assertEquals(BookingStatus.CONFIRMED, this.bookingRepository.findById(id).orElseThrow().getStatus());
	}

	@Test
	void deleteAfterFailedReleaseCallsReleaseAgain() throws Exception {
		String token = register(unique("saga-rel-retry"));
		long userId = userId(token);
		long id = insertBooking(userId, BookingStatus.CONFIRMED, unique("req-rel-retry"),
				Instant.parse("2026-10-06T00:00:00Z"));
		AtomicInteger calls = new AtomicInteger();
		doAnswer(invocation -> {
			if (calls.incrementAndGet() == 1) {
				throw new HotelClientException("down");
			}
			return null;
		}).when(this.hotelClient).release(anyLong(), any());

		MvcResult first = deleteBooking(token, id);
		MvcResult second = deleteBooking(token, id);

		assertEquals(503, first.getResponse().getStatus());
		assertEquals("CONFIRMED", json(first).get("status").asText());
		assertEquals(200, second.getResponse().getStatus());
		assertEquals("CANCELLED", json(second).get("status").asText());
		verify(this.hotelClient, times(2)).release(anyLong(), any());
	}

	@Test
	void deleteAfterSuccessDoesNotCallHotel() throws Exception {
		String token = register(unique("saga-del-twice"));
		long userId = userId(token);
		long id = insertBooking(userId, BookingStatus.CONFIRMED, unique("req-del-twice"),
				Instant.parse("2026-10-07T00:00:00Z"));

		MvcResult first = deleteBooking(token, id);
		MvcResult second = deleteBooking(token, id);

		assertEquals(200, first.getResponse().getStatus());
		assertEquals("CANCELLED", json(first).get("status").asText());
		assertEquals(200, second.getResponse().getStatus());
		assertEquals("CANCELLED", json(second).get("status").asText());
		verify(this.hotelClient, times(1)).release(anyLong(), any());
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

	private MvcResult deleteBooking(String token, long id) throws Exception {
		return this.mockMvc.perform(delete("/booking/" + id).header("Authorization", "Bearer " + token)).andReturn();
	}

	private long insertBooking(long userId, BookingStatus status, String requestId, Instant createdAt) {
		return new TransactionTemplate(this.transactionManager).execute(tx -> {
			BookingEntity entity = new BookingEntity();
			entity.setUser(this.userRepository.findById(userId).orElseThrow());
			entity.setRoomId(3L);
			entity.setStartDate(START);
			entity.setEndDate(END);
			entity.setStatus(status);
			entity.setRequestId(requestId);
			entity.setCreatedAt(createdAt);
			return this.bookingRepository.saveAndFlush(entity).getId();
		});
	}

	private long countByRequestId(String requestId) {
		return this.bookingRepository.findAll().stream().filter(row -> requestId.equals(row.getRequestId())).count();
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

	private static CreateBookingRequest booking(String requestId, boolean autoSelect, Long roomId, LocalDate start,
			LocalDate end) {
		return new CreateBookingRequest(requestId, autoSelect, roomId, start, end);
	}

	private static String unique(String prefix) {
		return prefix + "-" + UUID.randomUUID();
	}

	private static long userId(String token) throws Exception {
		return Long.parseLong(SignedJWT.parse(token).getJWTClaimsSet().getSubject());
	}

	private static MvcResult awaitDone(Future<MvcResult> first, Future<MvcResult> second) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			if (first.isDone()) {
				return first.get();
			}
			if (second.isDone()) {
				return second.get();
			}
			Thread.sleep(20);
		}
		throw new AssertionError("neither request finished while confirm was blocked");
	}

}
